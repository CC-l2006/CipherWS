package org.example.cipherws.note.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code note_admin_operation_log}。与用户日志同构，只是操作人换成管理员。 */
@Data
public class NoteAdminOperationLog {
    private Long id;
    private Long adminId;
    /** 操作类型：禁用用户 / 启用用户 / 重置 AI 配额 / 巡检笔记 / 修改 AI 配置 / 发布公告 … */
    private String type;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
