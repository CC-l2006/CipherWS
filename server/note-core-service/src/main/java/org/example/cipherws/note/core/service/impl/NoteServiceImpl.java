package org.example.cipherws.note.core.service.impl;

import jakarta.annotation.Resource;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos.NoteDetailVo;
import org.example.cipherws.note.core.dto.NoteDtos.NoteSaveRequest;
import org.example.cipherws.note.core.dto.NoteDtos.NoteVo;
import org.example.cipherws.note.core.dto.NoteDtos.SummaryVo;
import org.example.cipherws.note.core.dto.NoteDtos.TagBrief;
import org.example.cipherws.note.core.service.NoteService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 笔记业务实现，只用 JdbcTemplate（不引 MyBatis / JPA，多一层映射没换来收益）。
 *
 * <p>全文件三个固定做法：</p>
 * <ul>
 *   <li>每条 SQL 都带 {@code user_id} 条件。需求 6.2.2 要求越权被拦下，用 WHERE
 *       拦比「先查出来再判断」少一步，也不会因为漏写判断而放行；</li>
 *   <li>表名一律反引号：{@code `note`} 与 mysql 系统库同名，不包准踩坑；</li>
 *   <li>值全部走 {@code ?} 占位符，SQL 片段只拼接固定的列名与占位符个数。</li>
 * </ul>
 */
@Service
public class NoteServiceImpl implements NoteService {

    private static final String DEFAULT_TITLE = "无标题笔记";
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PRIVATE = "private";
    /** title 列是 VARCHAR(80)，超长提前截断，别让数据库抛 Data too long 变成「数据操作失败」 */
    private static final int MAX_TITLE_LENGTH = 80;
    /** 单页上限：size 来自 query 参数，允许客户端要 10 万条等于自己给自己挖坑 */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 列表与详情共用的列，避免两处 select 的字段悄悄跑偏 */
    private static final String NOTE_COLUMNS =
            "`id`, `title`, `content`, `status`, `flagged`, `created_at`, `updated_at`";

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result listNotes(long userId, String keyword, String status, Long tagId, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE n.`user_id` = ? AND n.`deleted` = 0");
        List<Object> args = new ArrayList<>();
        args.add(userId);

        String like = likePattern(keyword);
        if (like != null) {
            where.append(" AND (n.`title` LIKE ? OR n.`content` LIKE ?)");
            args.add(like);
            args.add(like);
        }
        String statusFilter = normalizeStatusFilter(status);
        if (statusFilter != null) {
            where.append(" AND n.`status` = ?");
            args.add(statusFilter);
        }
        if (tagId != null) {
            // 用 EXISTS 而不是 JOIN：一篇笔记挂多个标签时 JOIN 会把它变成多行
            where.append(" AND EXISTS (SELECT 1 FROM `note_tag_rel` r"
                    + " WHERE r.`note_id` = n.`id` AND r.`tag_id` = ?)");
            args.add(tagId);
        }
        return pagedNotes(userId, where, args, page, size);
    }

    @Override
    public Result listTrashNotes(long userId, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE n.`user_id` = ? AND n.`deleted` = 1");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        return pagedNotes(userId, where, args, page, size);
    }

    @Override
    @Transactional
    public Result clearTrash(long userId) {
        // ai_summary_record 没有指向 note 的外键，不先手动删就会留下永远打不开的孤儿摘要，
        // 管理员统计还会把它们算进 AI 调用次数里。
        jdbcTemplate.update("DELETE s FROM `ai_summary_record` s"
                + " JOIN `note` n ON n.`id` = s.`note_id`"
                + " WHERE n.`user_id` = ? AND n.`deleted` = 1 AND s.`user_id` = ?", userId, userId);
        // note_tag_rel 的两个外键都是 ON DELETE CASCADE，关联行随笔记一起消失，不用手删
        jdbcTemplate.update("DELETE FROM `note` WHERE `user_id` = ? AND `deleted` = 1", userId);
        return Result.ok(true);
    }

    @Override
    public Result getNote(long userId, long noteId) {
        NoteVo note = requireNoteVo(userId, noteId);
        // 总结历史同样带 user_id：这张表里 note_id 与 user_id 是两列，两个条件都写上才严密
        List<SummaryVo> summaries = jdbcTemplate.query(
                "SELECT `id`, `summary`, `created_at` FROM `ai_summary_record`"
                        + " WHERE `note_id` = ? AND `user_id` = ?"
                        + " ORDER BY `created_at` DESC, `id` DESC",
                (rs, rowNum) -> new SummaryVo(rs.getLong("id"), rs.getString("summary"),
                        toLocalDateTime(rs.getTimestamp("created_at"))),
                noteId, userId);
        NoteDetailVo detail = new NoteDetailVo(note.id(), note.title(), note.content(), note.status(),
                note.flagged(), note.createdAt(), note.updatedAt(), note.tags(), summaries);
        return Result.ok(detail);
    }

    @Override
    @Transactional
    public Result createNote(long userId, NoteSaveRequest request) {
        String title = normalizeTitle(request.title());
        String content = request.content() == null ? "" : request.content();
        String status = normalizeStatusForWrite(request.status());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `note` (`user_id`, `title`, `content`, `status`) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, title);
            ps.setString(3, content);
            ps.setString(4, status);
            return ps;
        }, keyHolder);

        long noteId = Objects.requireNonNull(keyHolder.getKey(), "新建笔记未返回主键").longValue();
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
        StringBuilder set = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (request.title() != null) {
            set.append("`title` = ?, ");
            args.add(normalizeTitle(request.title()));
        }
        if (request.content() != null) {
            set.append("`content` = ?, ");
            args.add(request.content());
        }
        if (request.status() != null) {
            set.append("`status` = ?, ");
            args.add(normalizeStatusForWrite(request.status()));
        }
        // 列表按 updated_at 倒序，行内容没变时 ON UPDATE 不会动它；
        // 显式 touch 一下，只改了标签或原地保存的笔记也能排到最前面
        set.append("`updated_at` = CURRENT_TIMESTAMP");
        args.add(noteId);
        args.add(userId);
        jdbcTemplate.update("UPDATE `note` SET " + set + " WHERE `id` = ? AND `user_id` = ?", args.toArray());

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
            jdbcTemplate.update("DELETE FROM `ai_summary_record` WHERE `note_id` = ? AND `user_id` = ?",
                    noteId, userId);
            jdbcTemplate.update("DELETE FROM `note` WHERE `id` = ? AND `user_id` = ?", noteId, userId);
        } else {
            // 进回收站：deleted=1 + 记录进站时间，正文原样留着，恢复时不用二次编辑
            jdbcTemplate.update("UPDATE `note` SET `deleted` = 1, `deleted_at` = CURRENT_TIMESTAMP,"
                    + " `updated_at` = CURRENT_TIMESTAMP WHERE `id` = ? AND `user_id` = ?", noteId, userId);
        }
        return Result.ok(true);
    }

    @Override
    @Transactional
    public Result restoreNote(long userId, long noteId) {
        requireNoteVo(userId, noteId);
        jdbcTemplate.update("UPDATE `note` SET `deleted` = 0, `deleted_at` = NULL,"
                + " `updated_at` = CURRENT_TIMESTAMP WHERE `id` = ? AND `user_id` = ?", noteId, userId);
        return Result.ok(true);
    }

    /**
     * 列表与回收站共用的翻页逻辑。
     *
     * <p>total 必须单独 COUNT：LIMIT 之后拿到的是当页条数，前端分页器要的是总条数，
     * 拿当页条数算页数永远只有一页。</p>
     */
    private Result pagedNotes(long userId, CharSequence where, List<Object> args, int page, int size) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `note` n" + where, Long.class, args.toArray());

        int limit = normalizeSize(size);
        int offset = (normalizePage(page) - 1) * limit;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(limit);
        pageArgs.add(offset);

        List<NoteVo> rows = jdbcTemplate.query(
                "SELECT " + NOTE_COLUMNS + " FROM `note` n" + where
                        + " ORDER BY n.`updated_at` DESC, n.`id` DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> toNoteVo(rs), pageArgs.toArray());

        // 当页笔记的标签一次查回来，避免每条笔记再查一次（N+1）
        Map<Long, List<TagBrief>> tagsByNote = loadTags(userId, rows.stream().map(NoteVo::id).toList());
        List<NoteVo> notes = rows.stream()
                .map(n -> new NoteVo(n.id(), n.title(), n.content(), n.status(), n.flagged(),
                        n.createdAt(), n.updatedAt(), tagsByNote.getOrDefault(n.id(), List.of())))
                .toList();
        return Result.ok(notes, total == null ? 0L : total);
    }

    /** 取笔记并校验归属：不存在与不属于当前用户，对调用方都是「笔记不存在」（不泄露别人有没有这条数据） */
    private NoteVo requireNoteVo(long userId, long noteId) {
        List<NoteVo> found = jdbcTemplate.query(
                "SELECT " + NOTE_COLUMNS + " FROM `note` n WHERE n.`id` = ? AND n.`user_id` = ?",
                (rs, rowNum) -> toNoteVo(rs), noteId, userId);
        if (found.isEmpty()) {
            throw new BusinessException("笔记不存在");
        }
        NoteVo note = found.get(0);
        List<TagBrief> tags = loadTags(userId, List.of(noteId)).getOrDefault(noteId, List.of());
        return new NoteVo(note.id(), note.title(), note.content(), note.status(), note.flagged(),
                note.createdAt(), note.updatedAt(), tags);
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
        jdbcTemplate.update("DELETE FROM `note_tag_rel` WHERE `note_id` = ?", noteId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        // LinkedHashSet 顺手去重：同一 tagId 传两次会撞 note_tag_rel 的联合主键
        for (Long tagId : new LinkedHashSet<>(tagIds)) {
            if (tagId == null) {
                continue;
            }
            jdbcTemplate.update("INSERT INTO `note_tag_rel` (`note_id`, `tag_id`)"
                            + " SELECT ?, `id` FROM `tag` WHERE `id` = ? AND `user_id` = ?",
                    noteId, tagId, userId);
        }
    }

    /**
     * 批量取标签，返回 noteId → 标签列表。
     *
     * <p>note_tag_rel 本身不带 user_id，所以关联到 {@code tag} 表后用
     * {@code t.user_id} 再兜一层，防止关联表里出现别人的标签。</p>
     */
    private Map<Long, List<TagBrief>> loadTags(long userId, List<Long> noteIds) {
        Map<Long, List<TagBrief>> grouped = new LinkedHashMap<>();
        if (noteIds == null || noteIds.isEmpty()) {
            return grouped;
        }
        String placeholders = String.join(", ", Collections.nCopies(noteIds.size(), "?"));
        List<Object> args = new ArrayList<>(noteIds);
        args.add(userId);
        String sql = "SELECT r.`note_id`, t.`id`, t.`name` FROM `note_tag_rel` r"
                + " JOIN `tag` t ON t.`id` = r.`tag_id`"
                + " WHERE r.`note_id` IN (" + placeholders + ") AND t.`user_id` = ?";
        RowCallbackHandler collector = rs -> grouped
                .computeIfAbsent(rs.getLong("note_id"), key -> new ArrayList<>())
                .add(new TagBrief(rs.getLong("id"), rs.getString("name")));
        jdbcTemplate.query(sql, collector, args.toArray());
        return grouped;
    }

    private static NoteVo toNoteVo(ResultSet rs) throws SQLException {
        return new NoteVo(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("status"),
                rs.getBoolean("flagged"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                List.of());
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
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

    /** status 只认 draft / private，其余（含 null、乱传的字符串）一律按 private 落库 */
    private static String normalizeStatusForWrite(String status) {
        return STATUS_DRAFT.equals(status) ? STATUS_DRAFT : STATUS_PRIVATE;
    }

    /** 查询条件里的 status：认不出来就当没传，别把「全部」变成「一条都没有」 */
    private static String normalizeStatusFilter(String status) {
        if (STATUS_DRAFT.equals(status)) {
            return STATUS_DRAFT;
        }
        if (STATUS_PRIVATE.equals(status)) {
            return STATUS_PRIVATE;
        }
        return null;
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
