package org.example.cipherws.note.core.service;

import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos.FolderSaveRequest;

/**
 * 文件夹业务：文件夹本身只是「一组标签的集合」，
 * 笔记通过标签自动归入，不存在手工拖拽的笔记-文件夹关联。
 */
public interface FolderService {

    /** 当前用户的文件夹，含绑定标签与自动归入的笔记数 */
    Result listFolders(long userId);

    Result createFolder(long userId, FolderSaveRequest request);

    Result updateFolder(long userId, long folderId, FolderSaveRequest request);

    /** 删文件夹只删文件夹与绑定，标签和笔记都不动 */
    Result removeFolder(long userId, long folderId);
}
