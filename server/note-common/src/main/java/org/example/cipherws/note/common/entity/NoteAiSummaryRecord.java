package org.example.cipherws.note.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code note_ai_summary_record}。同一篇笔记可有多条，不加唯一约束。 */
@Data
public class NoteAiSummaryRecord {
    private Long id;
    private Long noteId;
    private Long userId;
    private String sourceSnippet;
    private String summary;
    private Integer quotaCost;
    private LocalDateTime createdAt;
}
