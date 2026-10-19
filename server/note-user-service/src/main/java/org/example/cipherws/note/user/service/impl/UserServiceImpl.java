package org.example.cipherws.note.user.service.impl;

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
     * 账号允许的字符：中文、英文字母、数字、下划线。
     *
     * 支持中文是明确需求，所以不能再用 ^[a-zA-Z0-9_]+$ 把汉字挡在外面。
     * 范围取 CJK 基本汉字区（\u4e00-\u9fa5），覆盖 99% 的常用汉字；
     * 生僻字（扩展 B 区以后）在补充平面，用 char 表示会是代理对，
     * 需要 Pattern.UNICODE_CHARACTER_CLASS + \p{IsHan}，这里刻意不做，
     * 只保证「常用中文账号能用」，避免把校验写得太玄。
     */
    private static final Pattern USERNAME_ALLOWED = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_]+$");
    private static final int USERNAME_MIN = 2;
    private static final int USERNAME_MAX = 20;
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

    private static final String PURPOSE_RESET = "reset";
    private static final String PURPOSE_BIND = "bind";

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
    public UserVo register(String username, String password, String email) {
        String name = username == null ? "" : username.trim();
        String mail = email == null ? "" : email.trim();

        // 长度按「字符数」算，与建表时 VARCHAR(20) 的口径一致（MySQL 的
        // VARCHAR(20) 指 20 个字符而不是 20 字节，一个汉字占 1 个字符）。
        // 用 codePointCount 而不是 name.length()：后者会把代理对算成 2。
        int nameLen = codePointLength(name);
        if (nameLen < USERNAME_MIN || nameLen > USERNAME_MAX) {
            throw new BusinessException("账号需为 2-20 个字符");
        }
        if (!USERNAME_ALLOWED.matcher(name).matches()) {
            throw new BusinessException("账号只能包含中文、字母、数字或下划线");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("密码至少 6 位");
        }
        if (!EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
        // 先查一次是为了给出友好提示；真正的唯一性靠数据库唯一索引兜底
        if (userMapper.countByUsername(name) > 0) {
            throw new BusinessException("该账号已被注册");
        }

        NoteUser user = new NoteUser();
        user.setUsername(name);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("user");
        user.setEnabled(true);
        user.setEmail(mail);
        // 没填昵称时先用账号名占位，用户可以在资料页改
        user.setNickname(name);
        user.setAvatar("");
        user.setBio("");
        user.setAiQuota(INITIAL_QUOTA);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下两个人同时注册同一个账号时，唯一索引会在这里兜住
            throw new BusinessException("该账号已被注册");
        }
        // insert 已经把自增 id 回填进实体，但创建时间等列还是空，统一回查一次
        return toVo(userMapper.findById(user.getId()));
    }

    @Override
    public LoginRes login(String username, String password) {
        String name = username == null ? "" : username.trim();
        NoteUser user = userMapper.findByUsername(name);
        if (user == null) {
            // 不区分「账号不存在」与「密码错误」，避免被用来枚举账号
            throw new BusinessException("账号或密码不正确");
        }

        // 账号被禁用时必须给出明确原因（需求 4.2.3），否则用户永远不知道发生了什么
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("该账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new BusinessException("账号或密码不正确");
        }

        String token = JwtUtil.sign(user.getId(), user.getUsername(), user.getRole(),
                jwtSecret, jwtTtlSeconds);

        writeUserLog(user.getId(), "登录", "账号登录成功");
        return new LoginRes(token, profile(user.getId()));
    }

    @Override
    public SendCodeRes issueCode(Long currentUserId, String email, String purpose) {
        String mail = email == null ? "" : email.trim().toLowerCase();
        String use = PURPOSE_BIND.equalsIgnoreCase(purpose) ? PURPOSE_BIND : PURPOSE_RESET;

        if (!EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }

        if (PURPOSE_RESET.equals(use)) {
            // 找回密码：邮箱必须是某个已有账号的绑定邮箱
            if (userMapper.countByEmail(mail) == 0) {
                throw new BusinessException("该邮箱未注册");
            }
        } else {
            // 绑定邮箱：这个地址不能被别人占着
            if (currentUserId == null) {
                throw new BusinessException("未登录，请先登录");
            }
            NoteUser me = userMapper.findById(currentUserId);
            if (me != null && mail.equalsIgnoreCase(me.getEmail())) {
                throw new BusinessException("这个邮箱已经绑定到当前账号了");
            }
            if (userMapper.countByEmailExcluding(currentUserId, mail) > 0) {
                throw new BusinessException("该邮箱已被其他账号绑定");
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
            log.error("验证码邮件发送失败 to={} purpose={}", mail, use, e);
            throw new BusinessException("验证码邮件发送失败，请检查邮箱配置或稍后重试");
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
    public UserVo bindEmail(long userId, String email, String code) {
        String mail = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }

        NoteUser me = userMapper.findById(userId);
        if (me == null) {
            throw new BusinessException("用户不存在");
        }
        if (mail.equalsIgnoreCase(me.getEmail())) {
            throw new BusinessException("这个邮箱已经绑定到当前账号了");
        }
        if (userMapper.countByEmailExcluding(userId, mail) > 0) {
            throw new BusinessException("该邮箱已被其他账号绑定");
        }

        // 先验码再改库：验证码不对就什么都不动
        consumeCode(mail, PURPOSE_BIND, code);

        userMapper.updateEmail(userId, mail);
        writeUserLog(userId, "绑定邮箱", "绑定邮箱 " + maskEmail(mail));
        return profile(userId);
    }

    @Override
    public void changePassword(long userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码至少 6 位");
        }

        String stored = userMapper.findPasswordById(userId);
        if (stored == null || !passwordEncoder.matches(oldPassword == null ? "" : oldPassword, stored)) {
            throw new BusinessException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, stored)) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    /* ------------------------------ 内部工具 ------------------------------ */

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
            // 找回密码的码不能拿去绑邮箱，反之亦然
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
