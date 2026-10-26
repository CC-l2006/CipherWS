package org.example.cipherws.note.core.mapper;

import org.apache.ibatis.annotations.Param;
import org.example.cipherws.note.common.entity.NoteFolder;

import java.util.List;
import java.util.Map;

/**
 * 文件夹（{@code note_folder}）的数据访问接口。SQL 全部在 resources/mapper/FolderMapper.xml 里。
 *
 * <p>文件夹只是存放笔记的容器：笔记通过 {@code note.folder_id} 归入，
 * 不再有「文件夹-标签绑定」这张表。自动整理按「文件夹名 = 标签名」匹配。</p>
 *
 * <p>文件夹是用户私有数据，每个方法都带 {@code userId}。</p>
 */
public interface FolderMapper {

    /** 当前用户的文件夹（原始列名：id / name / created_at） */
    List<Map<String, Object>> selectFolders(@Param("userId") long userId);

    /** 每个文件夹下的未删除笔记数（folder_id / note_count） */
    List<Map<String, Object>> countNotesByFolder(@Param("userId") long userId);

    /** 归属校验：别人家的 folderId 一律当作不存在 */
    long countOwnedFolder(@Param("folderId") long folderId, @Param("userId") long userId);

    /** 插入后会把自增主键写回实体的 id */
    int insertFolder(NoteFolder folder);

    int updateFolderName(@Param("folderId") long folderId,
                         @Param("userId") long userId,
                         @Param("name") String name);

    /** 删文件夹前，把里面的笔记退回「未分类」 */
    int clearFolderNotes(@Param("folderId") long folderId, @Param("userId") long userId);

    int deleteFolder(@Param("folderId") long folderId, @Param("userId") long userId);

    /**
     * 自动整理：只处理**未分类**笔记，按「文件夹名 = 标签名」归入文件夹。
     * 返回被归类的笔记数。
     */
    int autoOrganize(@Param("userId") long userId);
}
