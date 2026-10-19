package org.example.cipherws.note.admin.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.note.admin.dto.AdminDtos.AnnouncementRequest;
import org.example.cipherws.note.admin.service.AdminService;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台公告管理：需求 4.6.4 管理员发布 / 编辑 / 下线 / 删除公告。
 *
 * <p>与普通用户侧的 {@link AnnouncementController} 分开：那边只读且只要 enabled=1，
 * 这边能看到全部并带写操作，靠 URL 前缀区分权限。</p>
 */
@RestController
@RequestMapping("/admin/announcements")
public class AdminAnnouncementController {

    @Resource
    private AdminService adminService;

    @GetMapping
    public Result list(@RequestParam(value = "page", required = false) Integer page,
                       @RequestParam(value = "size", required = false) Integer size,
                       @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.listAnnouncements(page, size);
    }

    @PostMapping
    public Result create(@RequestBody(required = false) AnnouncementRequest request,
                         @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                         @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.createAnnouncement(userId, request);
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable("id") Long id,
                         @RequestBody(required = false) AnnouncementRequest request,
                         @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                         @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.updateAnnouncement(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public Result remove(@PathVariable("id") Long id,
                         @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                         @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.removeAnnouncement(userId, id);
    }
}
