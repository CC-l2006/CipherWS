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

    /**
     * 注册：邮箱 + 验证码 + 密码 +（可选）昵称。
     *
     * 不再有 username —— 登录用邮箱，账号名由邮箱前缀自动生成，用户不用填，
     * 也就不会出现「用户名被占用」这种注册失败。
     *
     * @param email    要注册的邮箱，必须先用它收一次验证码（purpose=register）
     * @param code     6 位验证码
     * @param password 密码，≥6 位
     * @param nickname 昵称，可空（空则用邮箱前缀）
     */
    public record RegisterReq(String email, String code, String password, String nickname) {
    }

    /** 登录：邮箱 + 密码 */
    public record LoginReq(String email, String password) {
    }

    /** 登录成功返回：令牌 + 用户信息 */
    public record LoginRes(String token, UserVo user) {
    }

    /**
     * 修改资料。
     *
     * **没有 email** —— 换邮箱必须走「发验证码 + 校验」，否则任何人都能
     * 把自己的邮箱随便改成一个不属于自己的地址，然后拿它去重置别人的密码。
     * header 里的 avatar 只用于「清空头像」（传空串），正常更换走上传接口。
     */
    public record ProfileReq(String nickname, String bio, String avatar) {
    }

    /** 修改密码 */
    public record PasswordReq(String oldPassword, String newPassword) {
    }

    /** 找回密码：邮箱 + 验证码 + 新密码 */
    public record ResetPasswordReq(String email, String code, String password) {
    }

    /**
     * 发送验证码。
     *
     * @param purpose reset = 找回密码（邮箱必须已注册）；
     *                bind  = 绑定邮箱（邮箱不能被别的账号占用）。
     *                null 按 reset 处理，兼容老调用方。
     */
    public record SendCodeReq(String email, String purpose) {
    }

    /**
     * 发送验证码的结果。
     *
     * @param code 只在「没配 SMTP 的本地环境」且开了 note.auth.expose-code 时才有值，
     *             配了真邮箱就永远返回 null —— 那才是验证码该有的样子。
     * @param cooldown 距下次可发送的秒数，前端拿它做倒计时
     */
    public record SendCodeRes(String hint, String code, int cooldown) {
    }

    /** 绑定邮箱：新邮箱 + 验证码 */
    public record BindEmailReq(String email, String code) {
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
