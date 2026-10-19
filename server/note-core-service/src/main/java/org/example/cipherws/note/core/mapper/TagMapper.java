package org.example.cipherws.note.core.mapper;

import org.apache.ibatis.annotations.Param;
import org.example.cipherws.note.common.entity.NoteTag;

import java.util.List;
import java.util.Map;

/**
 * 标签表（{@code note_tag}）的数据访问接口。SQL 全部在 resources/mapper/TagMapper.xml 里。
 *
 * <p>标签是用户私有数据（需求 7.4），所以每个方法都带 {@code userId}，
 * 「这个标签是不是我的」这件事由 WHERE 直接兜住，Service 不用先查再判断。</p>
 *
 * <p>查询一律返回 {@code resultType="map"}：列表项要额外带一个算出来的
 * {@code note_count}，它不属于任何实体字段。**Map 的 key 是原始列名**，
 * {@code map-underscore-to-camel-case} 对 Map 不生效，所以是
 * {@code id} / {@code name} / {@code created_at} / {@code note_count}。</p>
 */
public interface TagMapper {

    /** 列表：带每个标签下当前用户未删除笔记的数量，按创建时间正序 */
    List<Map<String, Object>> selectTagsByUser(@Param("userId") long userId);

    /** 改名后回查用；tagId 与 userId 一起卡死，防止改到别人家的标签 */
    Map<String, Object> selectTagById(@Param("tagId") long tagId, @Param("userId") long userId);

    /** 新建后回查用：唯一键 (user_id, name) 保证查到的就是刚插进去的那一行 */
    Map<String, Object> selectTagByName(@Param("userId") long userId, @Param("name") String name);

    /** 插入后会把自增主键写回实体的 id（XML 里配了 useGeneratedKeys） */
    int insertTag(NoteTag tag);

    /** 改名（不叫 updateTag：这里确实只能改名字） */
    int updateTagName(@Param("tagId") long tagId,
                      @Param("userId") long userId,
                      @Param("name") String name);

    /**
     * 删除：只删标签自己。note_tag_rel 的两个外键都是 ON DELETE CASCADE，
     * 关联行自动清掉，笔记一篇都不动（需求 4.5.3）。
     */
    int deleteTag(@Param("tagId") long tagId, @Param("userId") long userId);

    /** 删改前的归属校验：别人家的 tagId 一律当作不存在 */
    long countOwnedTag(@Param("tagId") long tagId, @Param("userId") long userId);
}
