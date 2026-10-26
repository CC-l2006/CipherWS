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
            Long folderId,
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
            Long folderId,
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
     * 文件夹列表项。文件夹只是存放笔记的容器，不再绑定标签；
     * noteCount 是通过 note.folder_id 归入的文件数。
     */
    public record FolderVo(Long id, String name, Long noteCount) {
    }

    /** 新建 / 编辑文件夹：只要一个名字 */
    public record FolderSaveRequest(String name) {
    }

    /** 新建 / 更新笔记的请求体；更新时字段为 null 表示「这一项不改」 */
    public record NoteSaveRequest(String title, String content, String status, List<Long> tagIds) {
    }

    /** 把笔记归入某个文件夹（实际是补上该文件夹绑定的标签） */
    public record MoveNoteReq(Long folderId) {
    }

    /** 标签请求体，只有名字 */
    public record TagSaveRequest(String name) {
    }
}
