package org.example.cipherws.note.core.service.impl;

import org.example.cipherws.note.common.entity.Note;
import org.example.cipherws.note.common.entity.NoteAiSummaryRecord;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos.NoteDetailVo;
import org.example.cipherws.note.core.dto.NoteDtos.NoteSaveRequest;
import org.example.cipherws.note.core.dto.NoteDtos.NoteVo;
import org.example.cipherws.note.core.dto.NoteDtos.SummaryVo;
import org.example.cipherws.note.core.dto.NoteDtos.TagBrief;
import org.example.cipherws.note.core.mapper.NoteMapper;
import org.example.cipherws.note.core.service.NoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 笔记业务实现。数据访问全部走 {@link NoteMapper}（MyBatis），
 * 这一层只负责业务判断：校验、默认值、分页夹取、排序、异常文案。SQL 不在这个文件里。
 *
 * <p>三个固定做法：</p>
 * <ul>
 *   <li>每条数据访问都带 {@code userId} 条件。需求 6.2.2 要求越权被拦下，用 WHERE
 *       拦比「先查出来再判断」少一步，也不会因为漏写判断而放行；</li>
 *   <li>表名一律反引号，已全部收进 XML：{@code `note`} / {@code `note_tag`} /
 *       {@code `note_ai_summary_record`}；</li>
 *   <li>值全部走 Mapper 的命名参数，没有一处字符串拼接。</li>
 * </ul>
 */
@Service
public class NoteServiceImpl implements NoteService {

    private static final String DEFAULT_TITLE = "无标题笔记";
    /** 草稿 / 发布已取消：所有笔记统一是私有的「已发布」状态 */
    private static final String STATUS_PRIVATE = "private";
    /** title 列是 VARCHAR(80)，超长提前截断，别让数据库抛 Data too long 变成「数据操作失败」 */
    private static final int MAX_TITLE_LENGTH = 80;
    /** 单页上限：size 来自 query 参数，允许客户端要 10 万条等于自己给自己挖坑 */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** note.deleted 的两个取值，顺手给个名字，免得列表与回收站调用处出现魔数 */
    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    private final NoteMapper noteMapper;

    public NoteServiceImpl(NoteMapper noteMapper) {
        this.noteMapper = noteMapper;
    }

    @Override
    public Result listNotes(long userId, String keyword, Long folderId, List<Long> tagIds,
                            boolean untagged, int page, int size) {
        // LIKE 的 % / _ 转义留在这一层：占位符已经防了注入，这里防的是「搜 100% 却什么都命中」
        String like = likePattern(keyword);
        return pagedNotes(userId, NOT_DELETED, like, folderId, tagIds, untagged, page, size);
    }

    @Override
    public Result listTrashNotes(long userId, int page, int size) {
        return pagedNotes(userId, DELETED, null, null, null, false, page, size);
    }

    @Override
    @Transactional
    public Result clearTrash(long userId) {
        // ai_summary_record 没有指向 note 的外键，不先手动删就会留下永远打不开的孤儿摘要，
        // 管理员统计还会把它们算进 AI 调用次数里。
        noteMapper.deleteTrashSummaries(userId);
        // note_tag_rel 的两个外键都是 ON DELETE CASCADE，关联行随笔记一起消失，不用手删
        noteMapper.deleteTrashNotes(userId);
        return Result.ok(true);
    }

    @Override
    public Result getNote(long userId, long noteId) {
        NoteVo note = requireNoteVo(userId, noteId);
        // 总结历史同样带 user_id：这张表里 note_id 与 user_id 是两列，两个条件都写上才严密
        List<SummaryVo> summaries = noteMapper.selectSummaries(noteId, userId).stream()
                .map(NoteServiceImpl::toSummaryVo)
                .toList();
        NoteDetailVo detail = new NoteDetailVo(note.id(), note.title(), note.content(), note.status(),
                note.flagged(), note.createdAt(), note.updatedAt(), note.tags(), summaries);
        return Result.ok(detail);
    }

    @Override
    @Transactional
    public Result createNote(long userId, NoteSaveRequest request) {
        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(normalizeTitle(request.title()));
        note.setContent(request.content() == null ? "" : request.content());
        // 草稿/发布已取消：新建就是私有笔记
        note.setStatus(STATUS_PRIVATE);

        // 插入时已经配了 useGeneratedKeys，自增主键会回填到 note.id
        noteMapper.insertNote(note);
        long noteId = Objects.requireNonNull(note.getId(), "新建笔记未返回主键");

        bindTags(userId, noteId, request.tagIds());
        return Result.ok(requireNoteVo(userId, noteId));
    }

    @Override
    @Transactional
    public Result updateNote(long userId, long noteId, NoteSaveRequest request) {
        // 先确认这笔记是我的：改别人的和改不存在的，对调用方应该是同一个结果
        requireNoteVo(userId, noteId);

        // 只更新传了的字段。前端自动保存只发自己改过的那几项，
        // 把没传的当成「清空」会把用户正文一键洗掉。
        // null 交给 XML 里的 <if> 判空跳过；updated_at 由 SQL 无条件 touch。
        String title = request.title() == null ? null : normalizeTitle(request.title());
        String content = request.content();
        // 状态已取消：不再随请求变化，统一 private
        noteMapper.updateNoteFields(noteId, userId, title, content, STATUS_PRIVATE);

        if (request.tagIds() != null) {
            bindTags(userId, noteId, request.tagIds());
        }
        return Result.ok(requireNoteVo(userId, noteId));
    }

    @Override
    @Transactional
    public Result removeNote(long userId, long noteId, boolean hard) {
        requireNoteVo(userId, noteId);
        if (hard) {
            // 永久删除：摘要没有外键约束，必须手动清；关联行由外键级联
            noteMapper.deleteSummariesByNote(noteId, userId);
            noteMapper.deleteNote(noteId, userId);
        } else {
            // 进回收站：deleted=1 + 记录进站时间，正文原样留着，恢复时不用二次编辑
            noteMapper.softDeleteNote(noteId, userId);
        }
        return Result.ok(true);
    }

    @Override
    @Transactional
    public Result restoreNote(long userId, long noteId) {
        requireNoteVo(userId, noteId);
        noteMapper.restoreNote(noteId, userId);
        return Result.ok(true);
    }

    /**
     * 列表与回收站共用的翻页逻辑。
     *
     * <p>total 必须单独 COUNT：LIMIT 之后拿到的是当页条数，前端分页器要的是总条数，
     * 拿当页条数算页数永远只有一页。</p>
     */
    private Result pagedNotes(long userId, int deleted, String keywordPattern, Long folderId,
                              List<Long> tagIds, boolean untagged, int page, int size) {
        long total = noteMapper.countNotes(userId, deleted, keywordPattern, folderId, tagIds, untagged);

        int limit = normalizeSize(size);
        int offset = (normalizePage(page) - 1) * limit;

        List<Note> rows = noteMapper.selectNotes(
                userId, deleted, keywordPattern, folderId, tagIds, untagged, limit, offset);

        // 当页笔记的标签一次查回来，避免每条笔记再查一次（N+1）
        Map<Long, List<TagBrief>> tagsByNote = loadTags(userId, rows.stream().map(Note::getId).toList());
        List<NoteVo> notes = rows.stream()
                .map(n -> toNoteVo(n, tagsByNote.getOrDefault(n.getId(), List.of())))
                .toList();
        return Result.ok(notes, total);
    }

    /** 取笔记并校验归属：不存在与不属于当前用户，对调用方都是「笔记不存在」（不泄露别人有没有这条数据） */
    private NoteVo requireNoteVo(long userId, long noteId) {
        Note found = noteMapper.selectNoteById(noteId, userId);
        if (found == null) {
            throw new BusinessException("笔记不存在");
        }
        List<TagBrief> tags = loadTags(userId, List.of(noteId)).getOrDefault(noteId, List.of());
        return toNoteVo(found, tags);
    }

    /**
     * 重建笔记的标签关联。
     *
     * <p>调用前必须已经确认 noteId 属于当前用户：下面的 DELETE 只按 note_id 删，
     * 少了这层校验就会把别人的关联删掉。</p>
     *
     * <p>每条 INSERT 都带 {@code user_id} 条件：前端若把别人家的 tagId 塞进来，
     * SELECT 查不到行，插入 0 行，等于静默忽略 —— 既不会产生跨用户关联，
     * 也不用额外再查一次「这标签是不是我的」。</p>
     */
    private void bindTags(long userId, long noteId, List<Long> tagIds) {
        noteMapper.deleteTagRelations(noteId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        // LinkedHashSet 顺手去重：同一 tagId 传两次会撞 note_tag_rel 的联合主键
        for (Long tagId : new LinkedHashSet<>(tagIds)) {
            if (tagId == null) {
                continue;
            }
            noteMapper.insertTagRelation(noteId, tagId, userId);
        }
    }

    /**
     * 批量取标签，返回 noteId → 标签列表。
     *
     * <p>note_tag_rel 本身不带 user_id，所以关联到 {@code note_tag} 表后用
     * {@code t.user_id} 再兜一层，防止关联表里出现别人的标签。</p>
     *
     * <p>查询用的是 {@code resultType="map"}，**key 是原始列名**：
     * {@code note_id} / {@code id} / {@code name}。map-underscore-to-camel-case
     * 只作用于实体属性，对 Map 不生效，所以这里读的是带下划线的 key。</p>
     */
    private Map<Long, List<TagBrief>> loadTags(long userId, List<Long> noteIds) {
        Map<Long, List<TagBrief>> grouped = new LinkedHashMap<>();
        if (noteIds == null || noteIds.isEmpty()) {
            return grouped;
        }
        for (Map<String, Object> row : noteMapper.selectNoteTags(noteIds, userId)) {
            Long noteId = toLong(row.get("note_id"));
            grouped.computeIfAbsent(noteId, key -> new ArrayList<>())
                    .add(new TagBrief(toLong(row.get("id")), (String) row.get("name")));
        }
        return grouped;
    }

    private static NoteVo toNoteVo(Note note, List<TagBrief> tags) {
        return new NoteVo(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getStatus(),
                note.getFlagged(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                tags);
    }

    private static SummaryVo toSummaryVo(NoteAiSummaryRecord record) {
        return new SummaryVo(record.getId(), record.getSummary(), record.getCreatedAt());
    }

    /** Map 结果里的数字列可能是 Long / Integer / BigInteger，统一按 Number 取 long */
    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    /** 空标题兜底：列表页没有标题就没法点开，前端也依赖后端回填后的标题 */
    private static String normalizeTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_TITLE;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_TITLE_LENGTH) {
            return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_TITLE_LENGTH));
        }
        return trimmed;
    }

    /**
     * 关键字转 LIKE 模式。
     *
     * <p>{@code %} 和 {@code _} 是 LIKE 的通配符，用户搜「100%」时不该被当成通配符，
     * 否则会出现「随便搜什么都命中」的怪现象。占位符已经防了注入，这里是防通配符。</p>
     */
    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    /** size 缺省 / 非法给 10，超过 100 按 100 —— OFFSET 用负数会让 MySQL 直接报语法错 */
    private static int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
