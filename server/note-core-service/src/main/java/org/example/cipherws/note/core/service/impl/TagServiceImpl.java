package org.example.cipherws.note.core.service.impl;

import org.example.cipherws.note.common.entity.NoteTag;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos.TagSaveRequest;
import org.example.cipherws.note.core.dto.NoteDtos.TagVo;
import org.example.cipherws.note.core.mapper.TagMapper;
import org.example.cipherws.note.core.service.TagService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 标签业务实现。数据访问全部走 {@link TagMapper}（MyBatis），
 * 这一层只负责业务判断：名称校验、重名文案、异常文案。SQL 不在这个文件里。
 *
 * <p>标签是用户私有数据（需求 7.4），所以「这个标签是不是我的」这件事
 * 在每个写操作前都要单独确认一次，出错信息才有意义（「标签不存在」）。</p>
 */
@Service
public class TagServiceImpl implements TagService {

    /** tag.name 是 VARCHAR(12)：提前在服务层拦，别让数据库抛 Data too long */
    private static final int MAX_TAG_NAME_LENGTH = 12;

    private final TagMapper tagMapper;

    public TagServiceImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Override
    public Result listTags(long userId) {
        List<TagVo> tags = tagMapper.selectTagsByUser(userId).stream()
                .map(TagServiceImpl::toTagVo)
                .toList();
        return Result.ok(tags, (long) tags.size());
    }

    @Override
    public Result createTag(long userId, TagSaveRequest request) {
        String name = normalizeName(request.name());
        NoteTag tag = new NoteTag();
        tag.setUserId(userId);
        tag.setName(name);
        try {
            tagMapper.insertTag(tag);
        } catch (DuplicateKeyException e) {
            // 唯一键就是 (user_id, name)：交给数据库判定重名，先查再插在并发下会两边都插进去
            throw new BusinessException("同名标签已存在");
        }
        // 唯一键保证这里查到的就是刚插进去的那一行
        return Result.ok(toTagVo(tagMapper.selectTagByName(userId, name)));
    }

    @Override
    public Result renameTag(long userId, long tagId, TagSaveRequest request) {
        requireOwnedTag(userId, tagId);
        String name = normalizeName(request.name());
        try {
            tagMapper.updateTagName(tagId, userId, name);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("同名标签已存在");
        }
        return Result.ok(toTagVo(tagMapper.selectTagById(tagId, userId)));
    }

    @Override
    public Result removeTag(long userId, long tagId) {
        requireOwnedTag(userId, tagId);
        // 需求 4.5.3：删标签只解除关联，绝不删笔记。
        // note_tag_rel 的两个外键都是 ON DELETE CASCADE，删 tag 时关联行自动清掉，
        // 所以这里只需要删 tag 自己 —— 反过来从关联行去删 note 是绝对不能做的。
        tagMapper.deleteTag(tagId, userId);
        return Result.ok(true);
    }

    /** 删改前的归属校验：别人家的 tagId 一律当作不存在 */
    private void requireOwnedTag(long userId, long tagId) {
        if (tagMapper.countOwnedTag(tagId, userId) == 0) {
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

    /**
     * Map 行 -> 对外 VO。
     *
     * <p>XML 里这几条 select 用的是 {@code resultType="map"}（列表项要带算出来的
     * {@code note_count}，不是实体字段），所以拿到的 **key 是原始列名**：
     * {@code id} / {@code name} / {@code created_at} / {@code note_count}。
     * map-underscore-to-camel-case 对 Map 不生效，别写成 createdAt。</p>
     */
    private static TagVo toTagVo(Map<String, Object> row) {
        return new TagVo(
                toLong(row.get("id")),
                (String) row.get("name"),
                toLocalDateTime(row.get("created_at")),
                toLong(row.get("note_count")));
    }

    /** Map 结果里的数字列可能是 Long / Integer / BigInteger，统一按 Number 取 long */
    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    /** Map 结果里的 DATETIME 由 MyBatis 的默认类型处理器取成 Timestamp，这里转成 java.time */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        throw new IllegalStateException("无法识别的时间类型：" + value.getClass().getName());
    }
}
