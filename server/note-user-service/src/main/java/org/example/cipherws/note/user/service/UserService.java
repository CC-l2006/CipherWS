package org.example.cipherws.note.user.service;

import org.example.cipherws.note.user.dto.UserDtos.LoginRes;
import org.example.cipherws.note.user.dto.UserDtos.SendCodeRes;
import org.example.cipherws.note.user.dto.UserDtos.UserVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 账号与用户中心。
 *
 * 方法名用业务语言（register / issueCode / bindEmail），
 * 不用 doXxx / handleXxx —— 接口就是这份系统的词汇表。
 */
public interface UserService {

    /**
     * 注册（邮箱验证码制）。
     *
     * 流程：先 POST /auth/send-code（purpose=register）拿到码，再调这里。
     * 账号名不用填，由邮箱前缀自动生成 —— 这样不会再有「用户名被占用」。
     */
    UserVo register(String email, String code, String password, String nickname);

    /** 登录：用邮箱作为账号（email 上有唯一键） */
    LoginRes login(String email, String password);

    /**
     * 发验证码。
     *
     * @param currentUserId 当前登录用户；注册与找回密码时是 null（那时还没登录）
     * @param purpose       register（注册）/ reset（找回密码）/
     *                      bind_old（更换邮箱第 1 步：验证当前邮箱）/
     *                      bind_new（更换邮箱第 2 步：验证新邮箱）/
     *                      password（修改密码：验证当前邮箱）
     */
    SendCodeRes issueCode(Long currentUserId, String email, String purpose);

    /** 用验证码重置密码 */
    void resetPassword(String email, String code, String newPassword);

    /** 读当前登录用户资料 */
    UserVo profile(long userId);

    /** 改资料（昵称 / 简介 / 头像 URL）。**不含邮箱**，邮箱要走 bindEmail */
    UserVo updateProfile(long userId, String nickname, String bio, String avatar);

    /** 上传头像：校验图片 → 落盘 → 更新用户 → 顺带删除旧文件 */
    UserVo uploadAvatar(long userId, MultipartFile file);

    /**
     * 更换绑定邮箱第 1 步：校验发到**当前邮箱**的验证码。
     *
     * 通过后会记一个短时有效的「旧邮箱已验证」标记（10 分钟），
     * 第 2 步必须带着这个标记才能改，防止只拿到新邮箱就换绑。
     */
    void verifyOldEmail(long userId, String code);

    /**
     * 更换绑定邮箱第 2 步：验证**新邮箱**的验证码并写库。
     * 前置条件：最近 10 分钟内通过过 verifyOldEmail。
     */
    UserVo bindEmail(long userId, String email, String code);

    /** 改密码：必须提供旧密码 + 发到当前邮箱的验证码 */
    void changePassword(long userId, String oldPassword, String newPassword, String code);
}
