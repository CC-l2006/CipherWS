package org.example.cipherws.note.user.controller;

import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.example.cipherws.note.user.dto.UserDtos.BindEmailReq;
import org.example.cipherws.note.user.dto.UserDtos.LoginReq;
import org.example.cipherws.note.user.dto.UserDtos.LoginRes;
import org.example.cipherws.note.user.dto.UserDtos.PasswordReq;
import org.example.cipherws.note.user.dto.UserDtos.ProfileReq;
import org.example.cipherws.note.user.dto.UserDtos.RegisterReq;
import org.example.cipherws.note.user.dto.UserDtos.ResetPasswordReq;
import org.example.cipherws.note.user.dto.UserDtos.SendCodeReq;
import org.example.cipherws.note.user.dto.UserDtos.SendCodeRes;
import org.example.cipherws.note.user.dto.UserDtos.UserVo;
import org.example.cipherws.note.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证与用户中心。
 *
 * 没有类级 @RequestMapping，因为这里同时覆盖 /auth/**（网关白名单）
 * 和 /user/**（需要登录）两组路径 —— 前者不能要求 token，后者必须要求，
 * 分成两个 controller 只是多一个文件，写在一起反而更清楚边界在哪。
 *
 * 关于验证码的两个入口：
 * - {@code POST /auth/send-code}  找回密码用，不需要登录（那时还没登录）；
 * - {@code POST /user/email/code} 绑定邮箱用，**必须登录**。
 * 之所以不合成一个接口加个 purpose 参数：/auth/send-code 在网关白名单里，
 * 白名单分支不注入身份头，拿不到 userId 就没法校验「这个邮箱是不是别人的」。
 * 走两条路径，鉴权边界就天然分开了。
 */
@RestController
public class AccountController {

    private final UserService userService;

    public AccountController(UserService userService) {
        this.userService = userService;
    }

    /* ----------------------------- /auth/** ----------------------------- */

    @PostMapping("/auth/register")
    public Result register(@RequestBody RegisterReq req) {
        return Result.ok(userService.register(req.username(), req.password(), req.email()));
    }

    @PostMapping("/auth/login")
    public Result login(@RequestBody LoginReq req) {
        LoginRes res = userService.login(req.username(), req.password());
        return Result.ok(res);
    }

    /**
     * 退出登录。
     *
     * JWT 是无状态的，服务端没有会话可清 —— 真正的「退出」是前端丢掉 token。
     * 这里保留接口是为了让前端的调用链完整，将来若要支持「强制下线」，
     * 需要引入 token 黑名单（Redis）再在这里实现。
     */
    @PostMapping("/auth/logout")
    public Result logout() {
        return Result.ok(true);
    }

    /** 找回密码：邮箱必须已注册 */
    @PostMapping("/auth/send-code")
    public Result sendResetCode(@RequestBody SendCodeReq req) {
        SendCodeRes res = userService.issueCode(null, req.email(), "reset");
        return Result.ok(res);
    }

    @PostMapping("/auth/reset-password")
    public Result resetPassword(@RequestBody ResetPasswordReq req) {
        userService.resetPassword(req.email(), req.code(), req.password());
        return Result.ok(true);
    }

    /* ----------------------------- /user/** ----------------------------- */

    @GetMapping("/user/profile")
    public Result profile(@RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId) {
        return Result.ok(userService.profile(requireUserId(userId)));
    }

    /** 改昵称 / 简介 / 头像。**邮箱不在这里**，要走 /user/email 并验码 */
    @PutMapping("/user/profile")
    public Result updateProfile(
            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
            @RequestBody ProfileReq req) {
        UserVo vo = userService.updateProfile(
                requireUserId(userId), req.nickname(), req.bio(), req.avatar());
        return Result.ok(vo);
    }

    /** 绑定邮箱第一步：给**新邮箱**发验证码（需要登录） */
    @PostMapping("/user/email/code")
    public Result sendBindCode(
            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
            @RequestBody SendCodeReq req) {
        SendCodeRes res = userService.issueCode(requireUserId(userId), req.email(), "bind");
        return Result.ok(res);
    }

    /** 绑定邮箱第二步：带上收到的验证码换绑 */
    @PutMapping("/user/email")
    public Result bindEmail(
            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
            @RequestBody BindEmailReq req) {
        UserVo vo = userService.bindEmail(requireUserId(userId), req.email(), req.code());
        return Result.ok(vo);
    }

    /**
     * 上传头像（multipart/form-data，字段名 file）。
     * 大小与格式在 AvatarService 里校验，这里只负责把文件转给 service。
     */
    @PostMapping("/user/avatar")
    public Result uploadAvatar(
            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
            @RequestParam("file") MultipartFile file) {
        UserVo vo = userService.uploadAvatar(requireUserId(userId), file);
        return Result.ok(vo);
    }

    @PutMapping("/user/password")
    public Result changePassword(
            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
            @RequestBody PasswordReq req) {
        userService.changePassword(requireUserId(userId), req.oldPassword(), req.newPassword());
        return Result.ok(true);
    }

    /** 前端侧栏要显示「剩余 AI 额度」，单独给一个轻量接口，不必拉整个 profile */
    @GetMapping("/user/quota")
    public Result quota(@RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId) {
        UserVo vo = userService.profile(requireUserId(userId));
        return Result.ok(java.util.Map.of("aiQuota", vo.aiQuota()));
    }

    /**
     * 正常流程下网关已经拦过未登录请求，这里再判一次是**纵深防御**：
     * 万一有人绕过网关直连 8084，也不能让接口裸奔。
     */
    private long requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException("未登录，请先登录");
        }
        return userId;
    }
}
