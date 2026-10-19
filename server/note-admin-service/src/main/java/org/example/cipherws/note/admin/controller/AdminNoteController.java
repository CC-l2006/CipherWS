package org.example.cipherws.note.admin.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.note.admin.dto.AdminDtos.NoteFlagRequest;
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
 * 笔记巡检：需求 4.6.2 管理员只能标记违规，不能修改用户笔记正文。
 *
 * <p>这个类刻意只有两个接口：查列表、改 flagged。没有「编辑笔记」的映射，
 * 服务层对应的 UPDATE 也只碰 flagged 一列，需求 7.3 的只读约束在这里被焊死。</p>
 */
@RestController
@RequestMapping("/admin/notes")
public class AdminNoteController {

    @Resource
    private AdminService adminService;

    @GetMapping
    public Result inspect(@RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "page", required = false) Integer page,
                          @RequestParam(value = "size", required = false) Integer size,
                          @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.inspectNotes(keyword, page, size);
    }

    @PutMapping("/{id}/flag")
    public Result flag(@PathVariable("id") Long id,
                       @RequestBody(required = false) NoteFlagRequest request,
                       @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                       @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.flagNote(userId, id, request == null ? null : request.flagged());
    }
}
