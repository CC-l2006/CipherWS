package org.example.cipherws.note.core.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.example.cipherws.note.core.dto.NoteDtos.NoteSaveRequest;
import org.example.cipherws.note.core.service.NoteService;
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
 * 笔记接口：路径与入参和 frontend/note/src/api/index.js 的 noteApi 一一对应。
 * 网关 StripPrefix=1 已经剥掉 /api 前缀，所以这里直接映射 /notes。
 *
 * <p>身份只信任网关注入的请求头。X-User-Role 也一并读出，但本服务没有管理员接口，
 * 用不到它 —— 管理员巡检在 note-admin-service。</p>
 */
@RestController
@RequestMapping("/notes")
public class NoteController {

    @Resource
    private NoteService noteService;

    @GetMapping
    public Result listNotes(@RequestParam(value = "keyword", required = false) String keyword,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "tagId", required = false) Long tagId,
                            @RequestParam(value = "page", defaultValue = "1") int page,
                            @RequestParam(value = "size", defaultValue = "10") int size,
                            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                            @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return noteService.listNotes(requireLogin(userId), keyword, status, tagId, page, size);
    }

    @GetMapping("/trash")
    public Result listTrashNotes(@RequestParam(value = "page", defaultValue = "1") int page,
                                 @RequestParam(value = "size", defaultValue = "10") int size,
                                 @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                                 @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return noteService.listTrashNotes(requireLogin(userId), page, size);
    }

    @DeleteMapping("/trash")
    public Result clearTrash(@RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                             @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return noteService.clearTrash(requireLogin(userId));
    }

    @GetMapping("/{id}")
    public Result getNote(@PathVariable("id") long id,
                          @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                          @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return noteService.getNote(requireLogin(userId), id);
    }

    @PostMapping
    public Result createNote(@RequestBody NoteSaveRequest request,
                             @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                             @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return noteService.createNote(requireLogin(userId), request);
    }

    @PutMapping("/{id}")
    public Result updateNote(@PathVariable("id") long id,
                             @RequestBody NoteSaveRequest request,
                             @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                             @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return noteService.updateNote(requireLogin(userId), id, request);
    }

    @DeleteMapping("/{id}")
    public Result removeNote(@PathVariable("id") long id,
                             @RequestParam(value = "hard", defaultValue = "false") boolean hard,
                             @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                             @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return noteService.removeNote(requireLogin(userId), id, hard);
    }

    @PostMapping("/{id}/restore")
    public Result restoreNote(@PathVariable("id") long id,
                              @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                              @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return noteService.restoreNote(requireLogin(userId), id);
    }

    /** 没带 X-User-Id 说明请求不是网关转来的（或者网关没放行），一律按未登录处理 */
    private static long requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException("未登录，请先登录");
        }
        return userId;
    }
}
