package org.example.cipherws.note.core.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.example.cipherws.note.core.dto.NoteDtos.FolderSaveRequest;
import org.example.cipherws.note.core.service.FolderService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件夹接口：路径与 frontend/note/src/api/index.js 的 folderApi 一一对应。
 * 网关已剥掉 /api 前缀，所以这里直接映射 /folders。
 *
 * <p>文件夹只是存放笔记的容器，笔记通过 note.folder_id 直接归入；
 * 「自动整理」按「文件夹名 = 标签名」处理未分类笔记。</p>
 */
@RestController
@RequestMapping("/folders")
public class FolderController {

    @Resource
    private FolderService folderService;

    @GetMapping
    public Result listFolders(@RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                              @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return folderService.listFolders(requireLogin(userId));
    }

    @PostMapping
    public Result createFolder(@RequestBody FolderSaveRequest request,
                               @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                               @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return folderService.createFolder(requireLogin(userId), request);
    }

    @PutMapping("/{id}")
    public Result updateFolder(@PathVariable("id") long id,
                               @RequestBody FolderSaveRequest request,
                               @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                               @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return folderService.updateFolder(requireLogin(userId), id, request);
    }

    @DeleteMapping("/{id}")
    public Result removeFolder(@PathVariable("id") long id,
                               @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                               @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return folderService.removeFolder(requireLogin(userId), id);
    }

    /** 按「文件夹名 = 标签名」自动整理未分类笔记 */
    @PostMapping("/auto-organize")
    public Result autoOrganize(@RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                               @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return folderService.autoOrganize(requireLogin(userId));
    }

    private static long requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException("未登录，请先登录");
        }
        return userId;
    }
}
