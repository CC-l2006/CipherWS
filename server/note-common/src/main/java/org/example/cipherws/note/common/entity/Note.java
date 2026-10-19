package org.example.cipherws.note.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code note}。deleted=1 表示在回收站，flagged=1 表示被管理员标记违规。 */
@Data
public class Note {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    /** draft 草稿 / private 私有 */
    private String status;
    private Boolean deleted;
    private Boolean flagged;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
