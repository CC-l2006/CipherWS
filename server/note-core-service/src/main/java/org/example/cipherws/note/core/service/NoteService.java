package org.example.cipherws.note.core.service;

import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos;

/**
 * 笔记业务接口。
 *
 * <p>每个方法都把 {@code userId} 当必填参数收进来：需求 6.2.2 要求用户只能访问
 * 自己的笔记，身份从入口一路传到 SQL，「取不到用户」这种事就不会在实现内部
 * 变成一次「忘了加条件」的漏查。</p>
 */
public interface NoteService {

    /** 列表页：keyword 同时匹配标题与正文，status / tagId 为空表示不筛 */
    Result listNotes(long userId, String keyword, String status, Long tagId, int page, int size);

    /** 回收站：只含 deleted=1 的笔记 */
    Result listTrashNotes(long userId, int page, int size);

    /** 清空回收站（物理删除当前用户回收站里的笔记） */
    Result clearTrash(long userId);

    /** 详情：不存在或不属于当前用户都报「笔记不存在」 */
    Result getNote(long userId, long noteId);

    Result createNote(long userId, NoteDtos.NoteSaveRequest request);

    Result updateNote(long userId, long noteId, NoteDtos.NoteSaveRequest request);

    /** hard=true 永久删除，否则只进回收站 */
    Result removeNote(long userId, long noteId, boolean hard);

    Result restoreNote(long userId, long noteId);
}
