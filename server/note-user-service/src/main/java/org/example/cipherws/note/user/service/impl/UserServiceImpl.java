package org.example.cipherws.note.user.service.impl;

import org.example.cipherws.note.common.entity.NoteUser;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.security.JwtUtil;
import org.example.cipherws.note.user.dto.UserDtos.LoginRes;
import org.example.cipherws.note.user.dto.UserDtos.SendCodeRes;
import org.example.cipherws.note.user.dto.UserDtos.UserVo;
import org.example.cipherws.note.user.mapper.UserMapper;
import org.example.cipherws.note.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 用户服务实现。
 *
 * 数据访问全部走 {@link UserMapper}（MyBatis），这一层只负责业务判断：
 * 校验、加密、签发令牌、写日志。SQL 不在这个文件里。
 */
@Service
public class UserServiceImpl implements UserService {

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

    private final UserMapper userMapper;

    /** 只在用户服务里用，所以不放公共模块 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 验证码暂存：邮箱 -> (验证码, 过期时间戳)。
     * 放内存是刻意的取舍：单实例够用，且省掉一张表和一次清理任务。
     * 将来多实例部署时要换成 Redis，否则在 A 实例发的码到 B 实例校验不过。
     */
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    private record CodeEntry(String code, long expireAt) {
    }

    @Value("${note.jwt.secret}")
    private String jwtSecret;

    @Value("${note.jwt.ttl-seconds:7200}")
    private long jwtTtlSeconds;

    /**
     * 演示开关：把验证码回传给前端。
     * 默认 true 是为了让别人克隆下来就能跑通「找回密码」全流程；
     * 生产环境必须设 NOTE_EXPOSE_CODE=false，否则任何人拿到邮箱就能重置密码。
     */
    @Value("${note.auth.expose-code:true}")
    private boolean exposeCode;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserVo register(String username, String password, String email) {
        String name = username == null ? "" : username.trim();
        String mail = email == null ? "" : email.trim();

        // 长度按「字符数」算，与建表时 VARCHAR(20) 的口径一致（MySQL 的
        // VARCHAR(20) 指 20 个字符而不是 20 字节，一个汉字占 1 个字符）。
        // 用 codePointCount 而不是 name.length()：后者会把代理对算成 2。
        int nameLen = name.codePointCount(0, name.length());
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
    public SendCodeRes issueCode(String email) {
        String mail = email == null ? "" : email.trim();
        if (!EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
        if (userMapper.countByEmail(mail) == 0) {
            throw new BusinessException("该邮箱未注册");
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        codes.put(mail.toLowerCase(), new CodeEntry(code, System.currentTimeMillis() + 5 * 60_000L));

        // 真实环境应该走邮件发送；这里把验证码写日志，便于本地联调时不用配 SMTP
        System.out.printf("[note-user] 验证码邮件（模拟发送）to=%s code=%s%n", mail, code);

        String hint = exposeCode
                ? "演示环境：验证码已直接返回，无需查收邮件"
                : "验证码已发送，5 分钟内有效";
        return new SendCodeRes(hint, exposeCode ? code : null);
    }

    @Override
    public void resetPassword(String email, String code, String newPassword) {
        String mail = email == null ? "" : email.trim().toLowerCase();
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码至少 6 位");
        }

        CodeEntry entry = codes.get(mail);
        if (entry == null) {
            throw new BusinessException("请先获取验证码");
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            codes.remove(mail);
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!entry.code().equals(code == null ? "" : code.trim())) {
            throw new BusinessException("验证码不正确");
        }

        int updated = userMapper.updatePasswordByEmail(mail, passwordEncoder.encode(newPassword));
        if (updated == 0) {
            throw new BusinessException("该邮箱未注册");
        }
        // 用完立刻失效，避免同一个码被重复使用
        codes.remove(mail);
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
    public UserVo updateProfile(long userId, String nickname, String email, String bio, String avatar) {
        UserVo current = profile(userId);

        String name = nickname == null ? current.nickname() : nickname.trim();
        String mail = email == null ? current.email() : email.trim();
        String intro = bio == null ? current.bio() : bio.trim();
        String pic = avatar == null ? current.avatar() : avatar.trim();

        if (name.isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }
        if (name.length() > 20) {
            throw new BusinessException("昵称最多 20 个字符");
        }
        if (!mail.isEmpty() && !EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
        if (intro.length() > 120) {
            throw new BusinessException("个人简介最多 120 个字符");
        }

        userMapper.updateProfile(userId, name, mail, intro, pic);
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

    /** 登录等行为要落日志（需求 4.7.1）。日志失败不能影响主流程，所以单独 try。 */
    private void writeUserLog(long userId, String type, String detail) {
        try {
            userMapper.insertUserLog(userId, type, detail, "");
        } catch (Exception ignored) {
            // 日志表不可用不应该让用户登不上
        }
    }
}
