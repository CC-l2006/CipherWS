package org.example.cipherws.note.core.service.impl;

import jakarta.annotation.Resource;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos.TagSaveRequest;
import org.example.cipherws.note.core.dto.NoteDtos.TagVo;
import org.example.cipherws.note.core.service.TagService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标签业务实现。
 *
 * <p>标签是用户私有数据（需求 7.4），所以「这个标签是不是我的」这件事
 * 在每个写操作前都要单独确认一次，出错信息才有意义（「标签不存在」）。</p>
 */
@Service
public class TagServiceImpl implements TagService {

    /** tag.name 是 VARCHAR(12)：提前在服务层拦，别让数据库抛 Data too long */
    private static final int MAX_TAG_NAME_LENGTH = 12;

    /**
     * 标签列表项的统一查询片段，列表 / 新建 / 改名三处共用。
     *
     * <p>noteCount 用关联子查询算：只数当前用户、未删除的笔记 ——
     * 回收站里的笔记如果算进去，标签数量会虚高，用户点进去却看不到那么多。</p>
     */
    private static final String TAG_SELECT = "SELECT t.`id`, t.`name`, t.`created_at`,"
            + " (SELECT COUNT(*) FROM `note_tag_rel` r JOIN `note` n ON n.`id` = r.`note_id`"
            + " WHERE r.`tag_id` = t.`id` AND n.`user_id` = t.`user_id` AND n.`deleted` = 0) AS `note_count`"
            + " FROM `tag` t";

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result listTags(long userId) {
        List<TagVo> tags = jdbcTemplate.query(
                TAG_SELECT + " WHERE t.`user_id` = ? ORDER BY t.`created_at` ASC, t.`id` ASC",
                (rs, rowNum) -> toTagVo(rs), userId);
        return Result.ok(tags, (long) tags.size());
    }

    @Override
    public Result createTag(long userId, TagSaveRequest request) {
        String name = normalizeName(request.name());
        try {
            jdbcTemplate.update("INSERT INTO `tag` (`user_id`, `name`) VALUES (?, ?)", userId, name);
        } catch (DuplicateKeyException e) {
            // 唯一键就是 (user_id, name)：交给数据库判定重名，先查再插在并发下会两边都插进去
            throw new BusinessException("同名标签已存在");
        }
        // 唯一键保证这里查到的就是刚插进去的那一行
        return Result.ok(jdbcTemplate.queryForObject(TAG_SELECT + " WHERE t.`user_id` = ? AND t.`name` = ?",
                (rs, rowNum) -> toTagVo(rs), userId, name));
    }

    @Override
    public Result renameTag(long userId, long tagId, TagSaveRequest request) {
        requireOwnedTag(userId, tagId);
        String name = normalizeName(request.name());
        try {
            jdbcTemplate.update("UPDATE `tag` SET `name` = ? WHERE `id` = ? AND `user_id` = ?",
                    name, tagId, userId);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("同名标签已存在");
        }
        return Result.ok(jdbcTemplate.queryForObject(TAG_SELECT + " WHERE t.`id` = ? AND t.`user_id` = ?",
                (rs, rowNum) -> toTagVo(rs), tagId, userId));
    }

    @Override
    public Result removeTag(long userId, long tagId) {
        requireOwnedTag(userId, tagId);
        // 需求 4.5.3：删标签只解除关联，绝不删笔记。
        // note_tag_rel 的两个外键都是 ON DELETE CASCADE，删 tag 时关联行自动清掉，
        // 所以这里只需要删 tag 自己 —— 反过来从关联行去删 note 是绝对不能做的。
        jdbcTemplate.update("DELETE FROM `tag` WHERE `id` = ? AND `user_id` = ?", tagId, userId);
        return Result.ok(true);
    }

    /** 删改前的归属校验：别人家的 tagId 一律当作不存在 */
    private void requireOwnedTag(long userId, long tagId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `tag` WHERE `id` = ? AND `user_id` = ?", Long.class, tagId, userId);
        if (count == null || count == 0) {
            throw new BusinessException("标签不存在");
        }
    }

    /**
     * 标签名校验：去空格后不能为空、不能超过 12 个字符。
     *
     * <p>用 codePointCount 而不是 length：一个 emoji 在 Java 里占两个 char，
     * 用 length 会把 6 个 emoji 误判成 12 个字符。</p>
     */
    private static String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("标签名不能为空");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_TAG_NAME_LENGTH) {
            throw new BusinessException("标签名最多 12 个字符");
        }
        return trimmed;
    }

    private static TagVo toTagVo(ResultSet rs) throws SQLException {
        return new TagVo(
                rs.getLong("id"),
                rs.getString("name"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                rs.getLong("note_count"));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
