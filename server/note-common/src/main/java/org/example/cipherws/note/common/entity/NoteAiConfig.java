package org.example.cipherws.note.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code note_ai_config}。约定 id=1 那一行生效；apiKey 绝不能回传前端。 */
@Data
public class NoteAiConfig {
    private Long id;
    private String endpoint;
    private String apiKey;
    private String model;
    private Integer maxChars;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
