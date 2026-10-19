package org.example.cipherws.note.core.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.example.cipherws.note.core.dto.NoteDtos.TagSaveRequest;
import org.example.cipherws.note.core.service.TagService;
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
 * 标签接口：路径与入参和 frontend/note/src/api/index.js 的 tagApi 一一对应。
 * 网关已剥掉 /api 前缀，所以这里直接映射 /tags。
 */
@RestController
@RequestMapping("/tags")
public class TagController {

    @Resource
    private TagService tagService;

    @GetMapping
    public Result listTags(@RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                           @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return tagService.listTags(requireLogin(userId));
    }

    @PostMapping
    public Result createTag(@RequestBody TagSaveRequest request,
                            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                            @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return tagService.createTag(requireLogin(userId), request);
    }

    @PutMapping("/{id}")
    public Result renameTag(@PathVariable("id") long id,
                            @RequestBody TagSaveRequest request,
                            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                            @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return tagService.renameTag(requireLogin(userId), id, request);
    }

    @DeleteMapping("/{id}")
    public Result removeTag(@PathVariable("id") long id,
                            @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                            @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        return tagService.removeTag(requireLogin(userId), id);
    }

    /** 没带 X-User-Id 说明请求不是网关转来的（或者网关没放行），一律按未登录处理 */
    private static long requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException("未登录，请先登录");
        }
        return userId;
    }
}
