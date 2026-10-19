package org.example.cipherws.note.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code note_user}。
 *
 * 这些实体只做「一行记录」的容器，不含任何业务判断 —— 校验、权限、事务
 * 都在 Service 层。MyBatis 靠 map-underscore-to-camel-case 把 ai_quota
 * 映射到 aiQuota，所以字段名必须与列名的下划线写法一一对应。
 */
@Data
public class NoteUser {
    private Long id;
    private String username;
    /** BCrypt 摘要，绝不参与序列化到前端（VO 里没有这个字段） */
    private String password;
    private String role;
    private Boolean enabled;
    private String email;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer aiQuota;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
