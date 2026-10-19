package org.example.cipherws.note.user.service.impl;

import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.security.JwtUtil;
import org.example.cipherws.note.user.dto.UserDtos.LoginRes;
import org.example.cipherws.note.user.dto.UserDtos.SendCodeRes;
import org.example.cipherws.note.user.dto.UserDtos.UserVo;
import org.example.cipherws.note.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 用户服务实现。SQL 全部显式写在下面，不用 ORM —— 每条语句在做什么一目了然。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    // 统一用 ISO 8601（2026-10-01T14:20:00）。
    // 不能用 "yyyy-MM-dd HH:mm:ss" 的空格格式：那个在 Safari / iOS 上
    // new Date() 会返回 Invalid Date，前端「3 天前」这类相对时间就全成了空白。
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final JdbcTemplate jdbc;

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

    public UserServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<UserVo> USER_VO = (rs, i) -> new UserVo(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("nickname"),
            rs.getString("email"),
            rs.getString("role"),
            rs.getString("avatar"),
            rs.getString("bio"),
            rs.getInt("ai_quota"),
            rs.getBoolean("enabled"),
            rs.getTimestamp("created_at") == null ? null
                    : rs.getTimestamp("created_at").toLocalDateTime().format(TIME_FMT));

    @Override
    public UserVo register(String username, String password, String email) {
        String name = username == null ? "" : username.trim();
        String mail = email == null ? "" : email.trim();

        if (!USERNAME.matcher(name).matches()) {
            throw new BusinessException("账号需为 3-20 位字母、数字或下划线");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("密码至少 6 位");
        }
        if (!EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
        if (countByUsername(name) > 0) {
            throw new BusinessException("该账号已被注册");
        }

        try {
            jdbc.update("""
                    INSERT INTO `note_user` (username, password, role, enabled, email, nickname, ai_quota)
                    VALUES (?, ?, 'user', 1, ?, ?, 5)
                    """, name, passwordEncoder.encode(password), mail, name);
        } catch (DuplicateKeyException e) {
            // 并发下两个人同时注册同一个账号时，唯一索引会在这里兜住
            throw new BusinessException("该账号已被注册");
        }

        List<UserVo> created = jdbc.query(
                "SELECT * FROM `note_user` WHERE username = ?", USER_VO, name);
        return created.get(0);
    }

    @Override
    public LoginRes login(String username, String password) {
        String name = username == null ? "" : username.trim();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, password, role, enabled FROM `note_user` WHERE username = ?", name);
        if (rows.isEmpty()) {
            // 不区分「账号不存在」与「密码错误」，避免被用来枚举账号
            throw new BusinessException("账号或密码不正确");
        }
        Map<String, Object> row = rows.get(0);

        // 账号被禁用时必须给出明确原因（需求 4.2.3），否则用户永远不知道发生了什么
        if (!Boolean.TRUE.equals(toBool(row.get("enabled")))) {
            throw new BusinessException("该账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(password == null ? "" : password, String.valueOf(row.get("password")))) {
            throw new BusinessException("账号或密码不正确");
        }

        long userId = ((Number) row.get("id")).longValue();
        String role = String.valueOf(row.get("role"));
        String token = JwtUtil.sign(userId, name, role, jwtSecret, jwtTtlSeconds);

        writeUserLog(userId, "登录", "账号登录成功");
        return new LoginRes(token, profile(userId));
    }

    @Override
    public SendCodeRes issueCode(String email) {
        String mail = email == null ? "" : email.trim();
        if (!EMAIL.matcher(mail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
        if (countByEmail(mail) == 0) {
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

        int updated = jdbc.update("UPDATE `note_user` SET password = ? WHERE email = ?",
                passwordEncoder.encode(newPassword), mail);
        if (updated == 0) {
            throw new BusinessException("该邮箱未注册");
        }
        // 用完立刻失效，避免同一个码被重复使用
        codes.remove(mail);
    }

    @Override
    public UserVo profile(long userId) {
        List<UserVo> rows = jdbc.query("SELECT * FROM `note_user` WHERE id = ?", USER_VO, userId);
        if (rows.isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        return rows.get(0);
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

        jdbc.update("""
                UPDATE `note_user` SET nickname = ?, email = ?, bio = ?, avatar = ? WHERE id = ?
                """, name, mail, intro, pic, userId);
        return profile(userId);
    }

    @Override
    public void changePassword(long userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码至少 6 位");
        }

        String stored = jdbc.queryForObject(
                "SELECT password FROM `note_user` WHERE id = ?", String.class, userId);
        if (stored == null || !passwordEncoder.matches(oldPassword == null ? "" : oldPassword, stored)) {
            throw new BusinessException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, stored)) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        jdbc.update("UPDATE `note_user` SET password = ? WHERE id = ?",
                passwordEncoder.encode(newPassword), userId);
    }

    /* ------------------------------ 内部工具 ------------------------------ */

    private int countByUsername(String username) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM `note_user` WHERE username = ?", Integer.class, username);
        return n == null ? 0 : n;
    }

    private int countByEmail(String email) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM `note_user` WHERE email = ?", Integer.class, email);
        return n == null ? 0 : n;
    }

    /** 登录等行为要落日志（需求 4.7.1）。日志失败不能影响主流程，所以单独 try。 */
    private void writeUserLog(long userId, String type, String detail) {
        try {
            jdbc.update("""
                    INSERT INTO note_user_operation_log (user_id, type, detail, ip) VALUES (?, ?, ?, ?)
                    """, userId, type, detail, "");
        } catch (Exception ignored) {
            // 日志表不可用不应该让用户登不上
        }
    }

    private static Boolean toBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return false;
    }
}
