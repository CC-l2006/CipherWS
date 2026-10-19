package org.example.cipherws.note.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code note_user_operation_log}。只增不改不删（需求 4.7.4：日志不可删除）。 */
@Data
public class NoteUserOperationLog {
    private Long id;
    private Long userId;
    /** 操作类型：登录 / 新建笔记 / 删除笔记 / AI 总结 … */
    private String type;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
