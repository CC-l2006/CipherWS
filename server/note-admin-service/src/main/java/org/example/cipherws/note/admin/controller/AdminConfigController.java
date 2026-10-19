package org.example.cipherws.note.admin.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.note.admin.dto.AdminDtos.AiConfigRequest;
import org.example.cipherws.note.admin.service.AdminService;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 参数配置：需求 6.4.2 接口参数后台可配，改完即生效，不用改代码重启。
 *
 * <p>读写都只针对 {@code ai_config} 表 id = 1 那一行（建表脚本里约定了单行配置），
 * 所以接口没有 id 参数。</p>
 */
@RestController
@RequestMapping("/admin/ai-config")
public class AdminConfigController {

    @Resource
    private AdminService adminService;

    @GetMapping
    public Result read(@RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.getAiConfig();
    }

    @PutMapping
    public Result save(@RequestBody(required = false) AiConfigRequest request,
                       @RequestHeader(value = Auth.HEADER_USER_ID, required = false) Long userId,
                       @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.saveAiConfig(userId, request);
    }
}
