package org.example.cipherws.note.user.dto;

/**
 * 用户服务的数据传输对象。
 *
 * 全部用 record：只读、无需 setter，也不会有 Lombok 与 record 混用的歧义。
 * 一个文件装完，避免为 6 个只有三四个字段的类各建一个文件。
 */
public final class UserDtos {

    private UserDtos() {
    }

    /** 注册：账号 / 密码 / 邮箱 */
    public record RegisterReq(String username, String password, String email) {
    }

    /** 登录 */
    public record LoginReq(String username, String password) {
    }

    /** 登录成功返回：令牌 + 用户信息 */
    public record LoginRes(String token, UserVo user) {
    }

    /** 修改资料。字段都可空，只更新传了值的那些 */
    public record ProfileReq(String nickname, String email, String bio, String avatar) {
    }

    /** 修改密码 */
    public record PasswordReq(String oldPassword, String newPassword) {
    }

    /** 找回密码：邮箱 + 验证码 + 新密码 */
    public record ResetPasswordReq(String email, String code, String password) {
    }

    /** 发送验证码 */
    public record SendCodeReq(String email) {
    }

    /** 发送验证码的结果。code 只在「演示/开发」开关打开时才有值 */
    public record SendCodeRes(String hint, String code) {
    }

    /**
     * 对外的用户视图。
     * ⚠️ 绝对不能有 password 字段 —— 这是整条链路上最容易被顺手加进去的地方。
     */
    public record UserVo(
            Long id,
            String username,
            String nickname,
            String email,
            String role,
            String avatar,
            String bio,
            Integer aiQuota,
            Boolean enabled,
            String createdAt) {
    }
}
