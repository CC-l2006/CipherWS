package org.example.cipherws.note.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code note_tag}。标签属于用户私有，唯一键是 (user_id, name)。 */
@Data
public class NoteTag {
    private Long id;
    private Long userId;
    private String name;
    private LocalDateTime createdAt;
}
