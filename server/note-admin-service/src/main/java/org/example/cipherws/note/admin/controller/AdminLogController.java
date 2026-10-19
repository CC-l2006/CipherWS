package org.example.cipherws.note.admin.controller;

import jakarta.annotation.Resource;
import org.example.cipherws.note.admin.service.AdminService;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.common.security.Auth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志与 AI 调用统计。
 *
 * <p>需求 4.7.4：日志不可删除。所以这个类只有两个 GET，一个 DELETE 映射都不提供 ——
 * 不是前端不给按钮，而是后端根本没有删日志的能力。</p>
 */
@RestController
@RequestMapping("/admin")
public class AdminLogController {

    @Resource
    private AdminService adminService;

    /**
     * @param type user 查用户操作日志，admin 查管理员操作日志，其它值按 user 处理
     * @param from 起始日期 yyyy-MM-dd，含当天
     * @param to   结束日期 yyyy-MM-dd，含当天整天
     */
    @GetMapping("/logs")
    public Result logs(@RequestParam(value = "type", required = false) String type,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "from", required = false) String from,
                       @RequestParam(value = "to", required = false) String to,
                       @RequestParam(value = "page", required = false) Integer page,
                       @RequestParam(value = "size", required = false) Integer size,
                       @RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.listLogs(type, keyword, from, to, page, size);
    }

    @GetMapping("/stats")
    public Result stats(@RequestHeader(value = Auth.HEADER_USER_ROLE, required = false) String role) {
        Auth.requireAdmin(role);
        return adminService.stats();
    }
}
