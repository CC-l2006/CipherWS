package org.example.cipherws.note.core.mapper;

import org.apache.ibatis.annotations.Param;
import org.example.cipherws.note.common.entity.NoteFolder;

import java.util.List;
import java.util.Map;

/**
 * 文件夹（{@code note_folder} / {@code note_folder_tag}）的数据访问接口。
 * SQL 全部在 resources/mapper/FolderMapper.xml 里。
 *
 * <p>和标签一样，文件夹是用户私有数据，每个方法都带 {@code userId}；
 * 绑定标签时用 INSERT ... SELECT 校验标签归属，别人的 tagId 会插入 0 行。</p>
 */
public interface FolderMapper {

    /** 当前用户的文件夹（原始列名：id / name / created_at） */
    List<Map<String, Object>> selectFolders(@Param("userId") long userId);

    /** 当前用户所有文件夹绑定的标签（folder_id / tag_id / tag_name） */
    List<Map<String, Object>> selectFolderTagRows(@Param("userId") long userId);

    /** 每个文件夹下自动归入的未删除笔记数（folder_id / note_count，按笔记去重） */
    List<Map<String, Object>> countNotesByFolder(@Param("userId") long userId);

    /** 归属校验：别人家的 folderId 一律当作不存在 */
    long countOwnedFolder(@Param("folderId") long folderId, @Param("userId") long userId);

    /** 该文件夹绑定的标签 id；文件夹不属于当前用户时返回空列表 */
    List<Long> selectFolderTagIds(@Param("folderId") long folderId, @Param("userId") long userId);

    /** 插入后会把自增主键写回实体的 id */
    int insertFolder(NoteFolder folder);

    int updateFolderName(@Param("folderId") long folderId,
                         @Param("userId") long userId,
                         @Param("name") String name);

    /** 删文件夹：note_folder_tag 的外键 ON DELETE CASCADE 会自动清掉绑定 */
    int deleteFolder(@Param("folderId") long folderId, @Param("userId") long userId);

    /** 重建绑定前先清空 */
    int deleteFolderTags(@Param("folderId") long folderId);

    /**
     * 绑定一个标签：INSERT ... SELECT 同时校验「这个标签是不是当前用户的」，
     * 不是自己的标签则插入 0 行，等于静默忽略。
     */
    int insertFolderTag(@Param("folderId") long folderId,
                        @Param("tagId") long tagId,
                        @Param("userId") long userId);
}
