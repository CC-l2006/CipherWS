package org.example.cipherws.note.core.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记与标签的对外数据结构，全部收在这一个文件里。
 *
 * <p>为什么不直接返回实体 / 表行：{@code note} 表里的 {@code user_id}、
 * {@code deleted}、{@code deleted_at} 属于内部状态，前端一处都用不到；
 * 顺手带出去既让前端契约变脏，也等于给以后新增的敏感列留了后门。</p>
 */
public final class NoteDtos {

    private NoteDtos() {
    }

    /** 笔记里内嵌的标签：列表页只显示名字，不需要 userId 之类的字段 */
    public record TagBrief(Long id, String name) {
    }

    /** 笔记列表项，字段与 frontend/note/src/api/index.js 的 noteApi 一致 */
    public record NoteVo(
            Long id,
            String title,
            String content,
            String status,
            Boolean flagged,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<TagBrief> tags) {
    }

    /** 详情页里的 AI 总结记录（需求 4.4.5：同一笔记可多次总结） */
    public record SummaryVo(Long id, String summary, LocalDateTime createdAt) {
    }

    /** 详情 = 列表项 + 该笔记的 AI 总结历史 */
    public record NoteDetailVo(
            Long id,
            String title,
            String content,
            String status,
            Boolean flagged,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<TagBrief> tags,
            List<SummaryVo> summaries) {
    }

    /** 标签列表项：noteCount 只数当前用户未删除的笔记 */
    public record TagVo(Long id, String name, LocalDateTime createdAt, Long noteCount) {
    }

    /**
     * 文件夹列表项。
     *
     * <p>{@code tagIds / tagNames} 是绑定的标签，{@code noteCount} 是按这些标签
     * 自动归入该文件夹的未删除笔记数（去重）。</p>
     */
    public record FolderVo(
            Long id,
            String name,
            List<Long> tagIds,
            List<String> tagNames,
            Long noteCount) {
    }

    /** 新建 / 编辑文件夹：名字 + 要绑定的标签 */
    public record FolderSaveRequest(String name, List<Long> tagIds) {
    }

    /** 新建 / 更新笔记的请求体；更新时字段为 null 表示「这一项不改」 */
    public record NoteSaveRequest(String title, String content, String status, List<Long> tagIds) {
    }

    /** 标签请求体，只有名字 */
    public record TagSaveRequest(String name) {
    }
}
