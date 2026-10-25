package org.example.cipherws.note.user.service.impl;

import jakarta.mail.AuthenticationFailedException;
import org.example.cipherws.note.common.entity.NoteUser;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.security.JwtUtil;
import org.example.cipherws.note.user.dto.UserDtos.LoginRes;
import org.example.cipherws.note.user.dto.UserDtos.SendCodeRes;
import org.example.cipherws.note.user.dto.UserDtos.UserVo;
import org.example.cipherws.note.user.mapper.UserMapper;
import org.example.cipherws.note.user.service.AvatarService;
import org.example.cipherws.note.user.service.MailService;
import org.example.cipherws.note.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 用户服务实现。
 *
 * 数据访问全部走 {@link UserMapper}（MyBatis），这一层只负责业务判断：
 * 校验、加密、签发令牌、验证码、写日志。SQL 不在这个文件里。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /**
     * 邮箱格式校验。
     *
     * 2026-10-20 起**登录标识就是邮箱**（email 上有唯一键），所以这里只做
     * 「看起来像个邮箱」的最低限度校验，真正的归属验证靠一次性验证码。
     * 原来那套「账号 2-20 字符、允许中文」的规则已经废弃 —— 账号名现在由
     * 邮箱前缀自动生成，用户不用填，也就不会撞名。
     */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    // 统一用 ISO 8601（2026-10-01T14:20:00）。
    // 不能用 "yyyy-MM-dd HH:mm:ss" 的空格格式：那个在 Safari / iOS 上
    // new Date() 会返回 Invalid Date，前端「3 天前」这类相对时间就全成了空白。
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 新用户初始 AI 额度 */
    private static final int INITIAL_QUOTA = 5;

    /** 验证码有效期 5 分钟 */
    private static final long CODE_TTL_MILLIS = 5 * 60_000L;
    /** 同一个邮箱两次发送之间至少间隔 60 秒，防刷 */
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    /** 同一个码最多试错 5 次 */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private static final String PURPOSE_REGISTER = "register";
    private static final String PURPOSE_RESET = "reset";
    /** 更换邮箱第 1 步：验证**当前**邮箱 */
    private static final String PURPOSE_BIND_OLD = "bind_old";
    /** 更换邮箱第 2 步：验证**新**邮箱 */
    private static final String PURPOSE_BIND_NEW = "bind_new";
    /** 修改密码 */
    private static final String PURPOSE_PASSWORD = "password";

    private final UserMapper userMapper;
    private final MailService mailService;
    private final AvatarService avatarService;

    /** 只在用户服务里用，所以不放公共模块 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 验证码暂存：邮箱 -> 码。
     * 放内存是刻意的取舍：单实例够用，且省掉一张表和一次清理任务。
     * 将来多实例部署时要换成 Redis，否则在 A 实例发的码到 B 实例校验不过。
     */
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    /** 邮箱 -> 上次发送时间戳，用于 60 秒频控 */
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();

    /**
     * 用户 -> 「当前邮箱已验证」的过期时间戳（毫秒）。
     *
     * 更换绑定邮箱是两步的：先证明你是旧邮箱的主人，再证明新邮箱归你。
     * 第一步通过后在这里记一笔，第二步必须看到这笔记录才允许改库，
     * 否则「只要拿到新邮箱的验证码」就能换绑 —— 等于绕过了旧邮箱这道关。
     *
     * 只放内存：单实例够用；服务重启即失效，反而更安全（不需要清理任务）。
     */
    private final Map<Long, Long> oldEmailVerified = new ConcurrentHashMap<>();

    /** 第一步的验证结果保留 10 分钟，够用户去新邮箱收码 */
    private static final long OLD_VERIFY_TTL_MILLIS = 10 * 60_000L;

    /** 一个验证码的全部状态。attempts 可变，所以不用 record */
    private static final class CodeEntry {
        private final String code;
        private final String purpose;
        private final long expireAt;
        private int attempts;

        private CodeEntry(String code, String purpose, long expireAt) {
            this.code = code;
            this.purpose = purpose;
            this.expireAt = expireAt;
        }
    }

    @Value("${note.jwt.secret}")
    private String jwtSecret;

    @Value("${note.jwt.ttl-seconds:7200}")
    private long jwtTtlSeconds;

    /**
     * 演示开关：把验证码回传给前端。
     * 默认 true 是为了让别人克隆下来就能跑通「找回密码 / 绑定邮箱」全流程；
     * 生产环境必须设 NOTE_EXPOSE_CODE=false，否则任何人拿到邮箱就能重置密码。
     *
     * 注意：只有**没配 SMTP**时才会真的把码回给前端 —— 一旦配了真邮箱，
     * 这里就不再回传，免得配好了邮箱反而还在响应里漏码。
     */
    @Value("${note.auth.expose-code:true}")
    private boolean exposeCode;

    public UserServiceImpl(UserMapper userMapper, MailService mailService, AvatarService avatarService) {
        this.userMapper = userMapper;
        this.mailService = mailService;
        this.avatarService = avatarService;
    }

    @Override
    public UserVo register(String email, String code, String password, String nickname) {
        String mail = normalizeEmail(email);

        if (!EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("密码至少 6 位");
        }
        // 先查重：给出友好提示；真正的唯一性靠 uk_user_email 兜底
        if (userMapper.countByEmail(mail) > 0) {
            throw new BusinessException("该邮箱已注册，请直接登录");
        }

        // 验证码必须与这个邮箱匹配、且用途是「注册」——
        // 拿找回密码的码来注册会被 consumeCode 的用途校验拦下
        consumeCode(mail, PURPOSE_REGISTER, code);

        // 账号名由邮箱前缀生成，用户压根不用填，也就不会有「用户名被占用」
        String name = deriveUsername(mail);
        String nick = nickname == null ? "" : nickname.trim();
        if (nick.isEmpty()) {
            nick = name;
        }
        if (codePointLength(nick) > 20) {
            throw new BusinessException("昵称最多 20 个字符");
        }

        NoteUser user = new NoteUser();
        user.setUsername(name);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("user");
        user.setEnabled(true);
        user.setEmail(mail);
        user.setNickname(nick);
        user.setAvatar("");
        user.setBio("");
        user.setAiQuota(INITIAL_QUOTA);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 两个人同时拿同一个邮箱注册时，uk_user_email 在这里兜住
            throw new BusinessException("该邮箱已注册，请直接登录");
        }
        // insert 已经把自增 id 回填进实体，但创建时间等列还是空，统一回查一次
        return toVo(userMapper.findById(user.getId()));
    }

    @Override
    public LoginRes login(String email, String password) {
        String mail = normalizeEmail(email);
        if (mail.isEmpty()) {
            throw new BusinessException("请输入邮箱");
        }
        NoteUser user = userMapper.findByEmail(mail);
        if (user == null) {
            // 不区分「邮箱不存在」与「密码错误」，避免被用来枚举账号
            throw new BusinessException("邮箱或密码不正确");
        }

        // 账号被禁用时必须给出明确原因（需求 4.2.3），否则用户永远不知道发生了什么
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("该账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new BusinessException("邮箱或密码不正确");
        }

        // 令牌的 sub 放邮箱：它是登录标识，将来改昵称/显示名都不会影响身份
        String token = JwtUtil.sign(user.getId(), user.getEmail(), user.getRole(),
                jwtSecret, jwtTtlSeconds);

        writeUserLog(user.getId(), "登录", "邮箱登录成功");
        return new LoginRes(token, profile(user.getId()));
    }

    @Override
    public SendCodeRes issueCode(Long currentUserId, String email, String purpose) {
        String use = resolvePurpose(purpose);
        String mail;

        if (PURPOSE_REGISTER.equals(use) || PURPOSE_RESET.equals(use)) {
            // 未登录也能发的两种：注册 / 找回密码
            mail = normalizeEmail(email);
            if (!EMAIL.matcher(mail).matches()) {
                throw new BusinessException("邮箱格式不正确");
            }
            if (PURPOSE_REGISTER.equals(use)) {
                // 注册：这个邮箱还不能被注册过
                if (userMapper.countByEmail(mail) > 0) {
                    throw new BusinessException("该邮箱已注册，请直接登录");
                }
            } else {
                // 找回密码：邮箱必须是某个已有账号的登录邮箱
                if (userMapper.countByEmail(mail) == 0) {
                    throw new BusinessException("该邮箱未注册");
                }
            }
        } else {
            // 更换邮箱（两步）与改密码都要求登录
            if (currentUserId == null) {
                throw new BusinessException("未登录，请先登录");
            }
            NoteUser me = userMapper.findById(currentUserId);
            if (me == null) {
                throw new BusinessException("用户不存在");
            }
            String current = normalizeEmail(me.getEmail());

            if (PURPOSE_BIND_NEW.equals(use)) {
                // 第 2 步：发给**新**邮箱，且不能被别人占着
                mail = normalizeEmail(email);
                if (!EMAIL.matcher(mail).matches()) {
                    throw new BusinessException("邮箱格式不正确");
                }
                if (mail.equals(current)) {
                    throw new BusinessException("新邮箱与当前邮箱相同，无需更换");
                }
                if (userMapper.countByEmailExcluding(currentUserId, mail) > 0) {
                    throw new BusinessException("该邮箱已被其他账号绑定");
                }
            } else {
                // bind_old / password：一律发给**当前**邮箱，
                // 忽略请求体里的 email —— 否则调用方随便传个地址就能收码
                if (current.isEmpty()) {
                    throw new BusinessException("当前账号还没有绑定邮箱，无法发送验证码");
                }
                mail = current;
            }
        }

        // 60 秒频控：放在用途校验之后，避免「不存在也告诉你发太快」泄露信息
        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(mail);
        if (last != null) {
            long wait = RESEND_COOLDOWN_SECONDS - (now - last) / 1000;
            if (wait > 0) {
                throw new BusinessException("验证码发送过于频繁，请 " + wait + " 秒后再试");
            }
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        codes.put(mail, new CodeEntry(code, use, now + CODE_TTL_MILLIS));
        lastSentAt.put(mail, now);

        try {
            mailService.sendCode(mail, code, use);
        } catch (Exception e) {
            // 发信失败要回滚「已发送」状态：否则验证码在内存里躺着、用户却没收到，
            // 而且 60 秒频控还卡着不让重试，人会以为是服务坏了。
            codes.remove(mail);
            lastSentAt.remove(mail);

            // 把「为什么发不出去」区分开。
            // 之前所有失败都报同一句「请检查邮箱配置」，实际最常见的是
            // SMTP 535 认证被拒（授权码在邮箱后台重新生成后，旧码立即作废），
            // 光看那句提示根本判断不出来，只能翻日志。
            if (isMailAuthFailure(e)) {
                log.error("验证码邮件发送失败：SMTP 认证被拒（535 Login fail）to={} purpose={}。"
                        + "常见原因：授权码已失效（重新生成后旧码作废）、该邮箱未开启 SMTP 服务、"
                        + "或账号被临时限制登录。请到邮箱后台重新生成授权码后更新 deploy/mail.env.local。",
                        mail, use, e);
                throw new BusinessException("邮件服务暂不可用：服务器邮箱授权码已失效，请联系管理员");
            }
            if (isMailConnectFailure(e)) {
                log.error("验证码邮件发送失败：连不上 SMTP 服务器 to={} purpose={}。"
                        + "检查 SPRING_MAIL_HOST/PORT（465 走 SSL，587 走 STARTTLS）以及本机出网限制。",
                        mail, use, e);
                throw new BusinessException("邮件服务暂不可用：连不上邮件服务器，请稍后重试");
            }
            log.error("验证码邮件发送失败 to={} purpose={}", mail, use, e);
            throw new BusinessException("验证码邮件发送失败，请稍后重试");
        }

        boolean devMode = !mailService.isEnabled();
        String hint = devMode
                ? "本地未配置邮箱服务，验证码见服务端日志"
                : "验证码已发送至 " + mail + "，5 分钟内有效";
        // 只有「没配真邮箱」且开了演示开关，才把码回给前端
        String returnedCode = (devMode && exposeCode) ? code : null;
        return new SendCodeRes(hint, returnedCode, RESEND_COOLDOWN_SECONDS);
    }

    @Override
    public void resetPassword(String email, String code, String newPassword) {
        String mail = email == null ? "" : email.trim().toLowerCase();
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码至少 6 位");
        }

        consumeCode(mail, PURPOSE_RESET, code);

        int updated = userMapper.updatePasswordByEmail(mail, passwordEncoder.encode(newPassword));
        if (updated == 0) {
            throw new BusinessException("该邮箱未注册");
        }
    }

    @Override
    public UserVo profile(long userId) {
        NoteUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVo(user);
    }

    @Override
    public UserVo updateProfile(long userId, String nickname, String bio, String avatar) {
        UserVo current = profile(userId);

        String name = nickname == null ? current.nickname() : nickname.trim();
        String intro = bio == null ? current.bio() : bio.trim();
        String pic = avatar == null ? current.avatar() : avatar.trim();

        if (name.isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }
        // 昵称也按字符数算：中文昵称不该因为「三个字算三个字节」被长度限制卡住
        if (codePointLength(name) > 20) {
            throw new BusinessException("昵称最多 20 个字符");
        }
        if (codePointLength(intro) > 120) {
            throw new BusinessException("个人简介最多 120 个字符");
        }
        // 头像只允许两种来源：本站上传的 /uploads/**，或显式清空。
        // 不允许随便填外链 —— 那是个能把任意第三方资源塞进页面的口子。
        if (!pic.isEmpty() && !pic.startsWith("/uploads/")) {
            throw new BusinessException("头像请通过上传功能设置");
        }

        userMapper.updateProfile(userId, name, intro, pic);
        return profile(userId);
    }

    @Override
    public UserVo uploadAvatar(long userId, MultipartFile file) {
        UserVo current = profile(userId);
        String url = avatarService.save(userId, file);

        userMapper.updateAvatar(userId, url);
        // 新头像落库后再删旧文件：即使删失败也只是留个孤儿文件，不影响使用
        avatarService.deleteQuietly(current.avatar());

        writeUserLog(userId, "更换头像", "上传了新头像");
        return profile(userId);
    }

    @Override
    public void verifyOldEmail(long userId, String code) {
        NoteUser me = userMapper.findById(userId);
        if (me == null) {
            throw new BusinessException("用户不存在");
        }
        String current = normalizeEmail(me.getEmail());
        if (current.isEmpty()) {
            throw new BusinessException("当前账号还没有绑定邮箱，无法验证");
        }

        // 用途必须是 bind_old：拿「改密码」的码来验证旧邮箱会被拦下
        consumeCode(current, PURPOSE_BIND_OLD, code);

        // 记一个短时有效的标记，第 2 步改库时要检查它。
        // 只放内存：单实例够用，且这个标记天然随服务重启失效，比落库更安全。
        oldEmailVerified.put(userId, System.currentTimeMillis() + OLD_VERIFY_TTL_MILLIS);
        writeUserLog(userId, "验证邮箱", "通过验证码验证了当前邮箱 " + maskEmail(current));
    }

    @Override
    public UserVo bindEmail(long userId, String email, String code) {
        String mail = normalizeEmail(email);
        if (!EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }

        NoteUser me = userMapper.findById(userId);
        if (me == null) {
            throw new BusinessException("用户不存在");
        }
        String current = normalizeEmail(me.getEmail());
        if (mail.equals(current)) {
            throw new BusinessException("新邮箱与当前邮箱相同，无需更换");
        }
        if (userMapper.countByEmailExcluding(userId, mail) > 0) {
            throw new BusinessException("该邮箱已被其他账号绑定");
        }

        // 第 1 步必须已通过：否则「只要拿到新邮箱的码就能换绑」，
        // 等于把「旧邮箱还归你」这件事跳过去了。
        if (!current.isEmpty()) {
            Long exp = oldEmailVerified.get(userId);
            if (exp == null || exp < System.currentTimeMillis()) {
                throw new BusinessException("请先验证当前邮箱（验证码 10 分钟内有效）");
            }
        }

        // 先验码再改库：验证码不对就什么都不动
        consumeCode(mail, PURPOSE_BIND_NEW, code);

        userMapper.updateEmail(userId, mail);
        // 换绑成功后立刻作废标记，避免短时间内被反复利用
        oldEmailVerified.remove(userId);
        writeUserLog(userId, "更换绑定邮箱",
                "邮箱由 " + maskEmail(current) + " 更换为 " + maskEmail(mail));
        return profile(userId);
    }

    @Override
    public void changePassword(long userId, String oldPassword, String newPassword, String code) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码至少 6 位");
        }

        // 1) 先校验旧密码（最便宜的一步，错在这里就不用消耗验证码）
        String stored = userMapper.findPasswordById(userId);
        if (stored == null || !passwordEncoder.matches(oldPassword == null ? "" : oldPassword, stored)) {
            throw new BusinessException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, stored)) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        // 2) 再校验邮箱验证码：证明这个操作是账号主本人发起的
        NoteUser me = userMapper.findById(userId);
        String current = me == null ? "" : normalizeEmail(me.getEmail());
        if (current.isEmpty()) {
            throw new BusinessException("当前账号还没有绑定邮箱，无法通过验证码修改密码");
        }
        consumeCode(current, PURPOSE_PASSWORD, code);

        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        writeUserLog(userId, "修改密码", "通过邮箱验证码修改了密码");
    }

    /* ------------------------------ 内部工具 ------------------------------ */

    /**
     * 把前端传来的 purpose 归一化。
     *
     * 认不出来的一律按 reset 处理（兼容老调用方只传 email 的情况）。
     * 老的 "bind" 映射到 bind_new，只是为了让还没更新的旧前端不至于报错。
     */
    private static String resolvePurpose(String purpose) {
        if (purpose == null) {
            return PURPOSE_RESET;
        }
        String p = purpose.trim().toLowerCase();
        switch (p) {
            case PURPOSE_REGISTER:
                return PURPOSE_REGISTER;
            case PURPOSE_BIND_OLD:
                return PURPOSE_BIND_OLD;
            case PURPOSE_BIND_NEW:
            case "bind":
                return PURPOSE_BIND_NEW;
            case PURPOSE_PASSWORD:
                return PURPOSE_PASSWORD;
            default:
                return PURPOSE_RESET;
        }
    }

    /**
     * 邮箱统一转小写再比较/入库。
     *
     * 不这么做的话 `A@x.com` 与 `a@x.com` 会被当成两个账号：
     * 唯一键在 utf8mb4_0900_ai_ci 下虽然是大小写不敏感的，但代码里
     * equals 之类的判断不是，统一在入口处规范化最省事。
     */
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /**
     * 由邮箱前缀生成显示名（username）。
     *
     * username 现在只是「显示名」，没有唯一约束，所以撞了也没关系 ——
     * 这正是把登录标识换成邮箱的目的：注册永远不会因为重名失败。
     */
    private static String deriveUsername(String mail) {
        int at = mail.indexOf('@');
        String local = at > 0 ? mail.substring(0, at) : mail;
        // 只留字母数字下划线：VARCHAR(20) 且不再允许中文/特殊字符，
        // 中文邮箱前缀（极少见）会退化成 "user"
        String cleaned = local.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty()) {
            cleaned = "user";
        }
        if (cleaned.length() > 20) {
            cleaned = cleaned.substring(0, 20);
        }
        return cleaned;
    }

    /**
     * SMTP 认证被拒（535 / AuthenticationFailedException）。
     *
     * 沿 cause 链找，因为 JavaMail 会把真正的 AuthenticationFailedException
     * 包在 MailAuthenticationException 里，只看最外层判断不出来。
     */
    private static boolean isMailAuthFailure(Throwable e) {
        for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException) {
                return true;
            }
            String m = t.getMessage();
            if (m != null && (m.contains("535") || m.contains("Login fail"))) {
                return true;
            }
        }
        return false;
    }

    /** 连不上邮件服务器（DNS / 拒绝连接 / 超时），与「账号密码不对」是两类问题 */
    private static boolean isMailConnectFailure(Throwable e) {
        for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof UnknownHostException || t instanceof ConnectException
                    || t instanceof SocketTimeoutException) {
                return true;
            }
            String m = t.getMessage();
            if (m != null && (m.contains("Connection refused") || m.contains("timed out"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验并消费一个验证码。
     *
     * 「消费」是指校验通过后立刻从内存里删掉 —— 同一个码不能用第二次，
     * 否则被人瞄到一眼就能反复提交。
     */
    private void consumeCode(String mail, String purpose, String input) {
        CodeEntry entry = codes.get(mail);
        if (entry == null) {
            throw new BusinessException("请先获取验证码");
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            codes.remove(mail);
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!purpose.equals(entry.purpose)) {
            // 注册 / 找回密码 / 绑定邮箱三种码互不通用，防止拿 A 用途的码做 B 事
            throw new BusinessException("验证码用途不匹配，请重新获取");
        }
        if (entry.attempts >= MAX_CODE_ATTEMPTS) {
            codes.remove(mail);
            throw new BusinessException("验证码错误次数过多，请重新获取");
        }
        if (!entry.code.equals(input == null ? "" : input.trim())) {
            entry.attempts++;
            throw new BusinessException("验证码不正确");
        }
        codes.remove(mail);
    }

    /**
     * 实体 -> 对外 VO。
     *
     * 这一步看着啰嗦，但它是**唯一**能保证 password 不外泄的地方：
     * 实体里有密码摘要，VO 里根本没有这个字段，转换时不可能带出去。
     */
    private static UserVo toVo(NoteUser u) {
        return new UserVo(
                u.getId(),
                u.getUsername(),
                u.getNickname(),
                u.getEmail(),
                u.getRole(),
                u.getAvatar(),
                u.getBio(),
                u.getAiQuota(),
                u.getEnabled(),
                u.getCreatedAt() == null ? null : u.getCreatedAt().format(TIME_FMT));
    }

    /** 按 Unicode 码点算长度，一个汉字 = 1 */
    private static int codePointLength(String s) {
        return s.codePointCount(0, s.length());
    }

    /** 日志里别写完整邮箱：a***@example.com */
    private static String maskEmail(String mail) {
        int at = mail.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? mail.substring(at) : "");
        }
        return mail.charAt(0) + "***" + mail.substring(at);
    }

    /** 登录等行为要落日志（需求 4.7.1）。日志失败不能影响主流程，所以单独 try。 */
    private void writeUserLog(long userId, String type, String detail) {
        try {
            userMapper.insertUserLog(userId, type, detail, "");
        } catch (Exception ignored) {
            // 日志表不可用不应该让用户登不上
        }
    }
}
