package org.example.cipherws.note.core.service.impl;

import org.example.cipherws.note.common.entity.NoteFolder;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.result.Result;
import org.example.cipherws.note.core.dto.NoteDtos.FolderSaveRequest;
import org.example.cipherws.note.core.dto.NoteDtos.FolderVo;
import org.example.cipherws.note.core.mapper.FolderMapper;
import org.example.cipherws.note.core.service.FolderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件夹业务实现。SQL 全部在 FolderMapper.xml。
 *
 * <p>文件夹不再绑定标签；笔记通过 note.folder_id 直接归入。
 * 「自动整理」只处理未分类笔记，按「文件夹名 = 标签名」匹配。</p>
 */
@Service
public class FolderServiceImpl implements FolderService {

    /** note_folder.name 是 VARCHAR(20) */
    private static final int MAX_NAME_LENGTH = 20;

    private final FolderMapper folderMapper;

    public FolderServiceImpl(FolderMapper folderMapper) {
        this.folderMapper = folderMapper;
    }

    @Override
    public Result listFolders(long userId) {
        List<FolderVo> folders = loadFolders(userId);
        return Result.ok(folders, (long) folders.size());
    }

    @Override
    @Transactional
    public Result createFolder(long userId, FolderSaveRequest request) {
        String name = normalizeName(request == null ? null : request.name());
        NoteFolder folder = new NoteFolder();
        folder.setUserId(userId);
        folder.setName(name);
        try {
            folderMapper.insertFolder(folder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("同名文件夹已存在");
        }
        return Result.ok(findFolder(userId, folder.getId()));
    }

    @Override
    @Transactional
    public Result updateFolder(long userId, long folderId, FolderSaveRequest request) {
        requireOwned(userId, folderId);
        String name = normalizeName(request == null ? null : request.name());
        try {
            folderMapper.updateFolderName(folderId, userId, name);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("同名文件夹已存在");
        }
        return Result.ok(findFolder(userId, folderId));
    }

    @Override
    @Transactional
    public Result removeFolder(long userId, long folderId) {
        requireOwned(userId, folderId);
        // 笔记不删，只退回「未分类」
        folderMapper.clearFolderNotes(folderId, userId);
        folderMapper.deleteFolder(folderId, userId);
        return Result.ok(true);
    }

    @Override
    @Transactional
    public Result autoOrganize(long userId) {
        int moved = folderMapper.autoOrganize(userId);
        return Result.ok(moved);
    }

    /* ------------------------------ 内部实现 ------------------------------ */

    private List<FolderVo> loadFolders(long userId) {
        List<Map<String, Object>> rows = folderMapper.selectFolders(userId);

        Map<Long, Long> countByFolder = new HashMap<>();
        for (Map<String, Object> row : folderMapper.countNotesByFolder(userId)) {
            Long folderId = toLong(row.get("folder_id"));
            Long count = toLong(row.get("note_count"));
            if (folderId != null) {
                countByFolder.put(folderId, count == null ? 0L : count);
            }
        }

        List<FolderVo> folders = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            if (id == null) {
                continue;
            }
            folders.add(new FolderVo(
                    id,
                    (String) row.get("name"),
                    countByFolder.getOrDefault(id, 0L)));
        }
        return folders;
    }

    private FolderVo findFolder(long userId, long folderId) {
        return loadFolders(userId).stream()
                .filter(folder -> folder.id() != null && folder.id() == folderId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("文件夹不存在"));
    }

    private void requireOwned(long userId, long folderId) {
        if (folderMapper.countOwnedFolder(folderId, userId) == 0) {
            throw new BusinessException("文件夹不存在");
        }
    }

    private static String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("文件夹名不能为空");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_NAME_LENGTH) {
            throw new BusinessException("文件夹名最多 20 个字符");
        }
        return trimmed;
    }

    /** Map 结果里的数字列可能是 Long / Integer / BigInteger，统一按 Number 取 long */
    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
