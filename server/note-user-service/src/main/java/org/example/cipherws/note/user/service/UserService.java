package org.example.cipherws.note.user.service;

import org.example.cipherws.note.user.dto.UserDtos.LoginRes;
import org.example.cipherws.note.user.dto.UserDtos.SendCodeRes;
import org.example.cipherws.note.user.dto.UserDtos.UserVo;

/**
 * 账号与用户中心。
 *
 * 方法名用业务语言（register / issueCode / resetPassword），
 * 不用 doXxx / handleXxx —— 接口就是这份系统的词汇表。
 */
public interface UserService {

    /** 注册普通用户（需求 4.1.1：账号唯一、密码加密存储） */
    UserVo register(String username, String password, String email);

    /** 登录（需求 4.1.2：校验身份、区分普通用户与管理员） */
    LoginRes login(String username, String password);

    /** 发验证码（需求 4.1.5：通过邮箱验证码重置密码） */
    SendCodeRes issueCode(String email);

    /** 用验证码重置密码 */
    void resetPassword(String email, String code, String newPassword);

    /** 读当前登录用户资料 */
    UserVo profile(long userId);

    /** 改资料（昵称 / 邮箱 / 简介 / 头像） */
    UserVo updateProfile(long userId, String nickname, String email, String bio, String avatar);

    /** 改密码（需求 4.2.2：必须先验证旧密码） */
    void changePassword(long userId, String oldPassword, String newPassword);
}
