package org.example.cipherws.note.core.mapper;

import org.apache.ibatis.annotations.Param;
import org.example.cipherws.note.common.entity.Note;
import org.example.cipherws.note.common.entity.NoteAiSummaryRecord;

import java.util.List;
import java.util.Map;

/**
 * 笔记相关表（{@code note} / {@code note_tag_rel} / {@code note_ai_summary_record}）
 * 的数据访问接口。SQL 全部在 resources/mapper/NoteMapper.xml 里。
 *
 * <p>三条贯穿全接口的约定：</p>
 * <ul>
 *   <li>凡是操作某篇笔记，参数里都带 {@code userId}，由 WHERE 直接拦越权 ——
 *       改别人的和改不存在的，SQL 层面就是同一个结果（影响 0 行）；</li>
 *   <li>动态条件（关键词 / 状态 / 标签）在 XML 里用 {@code <if>}，调用方按需传 null；</li>
 *   <li>批量 IN 用 {@code <foreach>}，返回 Map 的投影结果见各方法注释 ——
 *       {@code map-underscore-to-camel-case} 对 Map 不生效，key 是原始列名。</li>
 * </ul>
 */
public interface NoteMapper {

    /**
     * 列表 / 回收站共用的计数。{@code deleted} 传 0 表示正常列表、1 表示回收站。
     *
     * <p>total 必须单独 COUNT：LIMIT 之后拿到的是当页条数，前端分页器要的是总条数。</p>
     */
    long countNotes(@Param("userId") long userId,
                    @Param("deleted") int deleted,
                    @Param("keywordPattern") String keywordPattern,
                    @Param("folderId") Long folderId,
                    @Param("tagIds") List<Long> tagIds,
                    @Param("untagged") boolean untagged);

    /**
     * 列表 / 回收站共用的翻页查询，按 updated_at DESC, id DESC 排序。
     *
     * <p>{@code keywordPattern} 是已经转义并包好 {@code %} 的 LIKE 模式，
     * 转义逻辑留在 Service，这里只负责 {@code LIKE #{keywordPattern}}。</p>
     */
    List<Note> selectNotes(@Param("userId") long userId,
                           @Param("deleted") int deleted,
                           @Param("keywordPattern") String keywordPattern,
                           @Param("folderId") Long folderId,
                           @Param("tagIds") List<Long> tagIds,
                           @Param("untagged") boolean untagged,
                           @Param("limit") int limit,
                           @Param("offset") int offset);

    /** 按 id + user_id 取一篇（不筛 deleted，所以回收站里的笔记详情照样能打开） */
    Note selectNoteById(@Param("noteId") long noteId, @Param("userId") long userId);

    /** 详情页的 AI 总结历史：note_id 与 user_id 两个条件都带上才严密 */
    List<NoteAiSummaryRecord> selectSummaries(@Param("noteId") long noteId, @Param("userId") long userId);

    /** 插入后会把自增主键写回实体的 id（XML 里配了 useGeneratedKeys） */
    int insertNote(Note note);

    /**
     * 只更新传了的字段：null 表示「这一项不改」，把没传的当成清空会把用户正文一键洗掉。
     * updated_at 无论如何都会被 touch 一下。
     */
    int updateNoteFields(@Param("noteId") long noteId,
                         @Param("userId") long userId,
                         @Param("title") String title,
                         @Param("content") String content,
                         @Param("status") String status);

    /** 进回收站：deleted=1 + 记录进站时间，正文原样留着 */
    int softDeleteNote(@Param("noteId") long noteId, @Param("userId") long userId);

    /** 从回收站恢复：deleted=0、deleted_at 清空 */
    int restoreNote(@Param("noteId") long noteId, @Param("userId") long userId);

    /** 永久删除笔记本体（关联行由外键 ON DELETE CASCADE 带走） */
    int deleteNote(@Param("noteId") long noteId, @Param("userId") long userId);

    /** 永久删除单篇笔记的 AI 摘要：这张表没有外键，必须先手动清 */
    int deleteSummariesByNote(@Param("noteId") long noteId, @Param("userId") long userId);

    /**
     * 清空回收站第一步：删掉当前用户回收站里那些笔记的摘要。
     *
     * <p>{@code note_ai_summary_record} 没有指向 note 的外键，不先删就会留下永远打不开的
     * 孤儿摘要，管理员统计还会把它们算进 AI 调用次数里。</p>
     */
    int deleteTrashSummaries(@Param("userId") long userId);

    /** 清空回收站第二步：物理删除 deleted=1 的笔记（关联行由外键级联） */
    int deleteTrashNotes(@Param("userId") long userId);

    /** 重建关联前先清空。调用方必须已经确认这笔记属于当前用户，否则会删掉别人的关联 */
    int deleteTagRelations(@Param("noteId") long noteId);

    /**
     * 建一条笔记-标签关联。
     *
     * <p>刻意写成 INSERT ... SELECT 而不是 VALUES：前端若把别人家的 tagId 塞进来，
     * SELECT 查不到行，插入 0 行，等于静默忽略 —— 既不会产生跨用户关联，
     * 也不用额外再查一次「这标签是不是我的」。</p>
     */
    int insertTagRelation(@Param("noteId") long noteId,
                          @Param("tagId") long tagId,
                          @Param("userId") long userId);

    /**
     * 批量取这批笔记的标签（一次查回来，避免每条笔记再查一次）。
     *
     * <p>{@code resultType="map"} 的 key 是**原始列名**（下划线）：
     * {@code note_id}（Long）、{@code id}（Long）、{@code name}（String）。
     * 因为不是实体，没法定 resultType=NoteTag —— 这里要的是 note_id 这个投影列。</p>
     */
    List<Map<String, Object>> selectNoteTags(@Param("noteIds") List<Long> noteIds,
                                             @Param("userId") long userId);
}
