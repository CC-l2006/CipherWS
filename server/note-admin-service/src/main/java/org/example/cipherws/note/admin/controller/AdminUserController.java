package org.example.cipherws.note.admin.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.note.admin.dto.AdminDtos.UserQuotaRequest;
import org.example.cipherws.note.admin.dto.AdminDtos.UserStatusRequest;
import org.example.cipherws.note.admin.service.AdminService;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理：需求 4.6.1 用户检索、禁用 / 启用、AI 额度分配。
 *
 * <p>每个方法第一行的 {@code Auth.requireAdmin(role)} 是硬约束：需求 4.1.4 要求
 * 普通用户没有后台权限，前端路由守卫只负责体验，真正的拦截必须在后端做。
 * 网关注入的身份头缺失时 role 为 null，同样会被拒。</p>
 */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Resource
    private AdminService adminService;

    @GetMapping
    public Result list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "page", required = false) Integer page,
                       @RequestParam(value = "size", required = false) Integer size,
                       @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.listUsers(keyword, page, size);
    }

    @PutMapping("/{id}/status")
    public Result setStatus(@PathVariable("id") Long id,
                            @RequestBody(required = false) UserStatusRequest request,
                            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                            @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.toggleUserStatus(userId, id, request == null ? null : request.enabled());
    }

    @PutMapping("/{id}/quota")
    public Result setQuota(@PathVariable("id") Long id,
                           @RequestBody(required = false) UserQuotaRequest request,
                           @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                           @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.resetQuota(userId, id, request == null ? null : request.aiQuota());
    }
}
