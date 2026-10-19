package org.example.cipherws.note.core.service;

import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos;

/**
 * 标签业务接口。标签属于用户私有（需求 7.4），所以同样每个方法都要 userId。
 */
public interface TagService {

    /** 列表：带每个标签下当前用户未删除笔记的数量 */
    Result listTags(long userId);

    Result createTag(long userId, NoteDtos.TagSaveRequest request);

    /** 改名（不叫 updateTag：这里确实只能改名字） */
    Result renameTag(long userId, long tagId, NoteDtos.TagSaveRequest request);

    /** 删除：只删标签与关联行，笔记一篇都不动（需求 4.5.3） */
    Result removeTag(long userId, long tagId);
}
