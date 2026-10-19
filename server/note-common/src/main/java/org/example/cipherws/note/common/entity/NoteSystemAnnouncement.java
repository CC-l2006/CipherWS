package org.example.cipherws.note.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code note_system_announcement}。enabled=0 的公告普通用户看不到。 */
@Data
public class NoteSystemAnnouncement {
    private Long id;
    private String title;
    private String content;
    private Long adminId;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
