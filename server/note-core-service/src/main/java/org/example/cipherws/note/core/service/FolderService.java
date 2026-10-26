package org.example.cipherws.note.core.service;

import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos.FolderSaveRequest;

/**
 * 文件夹业务：文件夹只是存放笔记的容器，笔记通过 {@code note.folder_id} 直接归入。
 * 「自动整理」按「文件夹名 = 标签名」把未分类笔记归类，不需要提前绑定标签。
 */
public interface FolderService {

    /** 当前用户的文件夹，含每个文件夹的笔记数 */
    Result listFolders(long userId);

    Result createFolder(long userId, FolderSaveRequest request);

    Result updateFolder(long userId, long folderId, FolderSaveRequest request);

    /** 删文件夹只删文件夹本身，里面的笔记退回「未分类」 */
    Result removeFolder(long userId, long folderId);

    /** 按同名标签整理未分类笔记，返回本次归类的笔记数 */
    Result autoOrganize(long userId);
}
