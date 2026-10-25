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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 文件夹业务实现。SQL 全部在 FolderMapper.xml。
 *
 * <p>文件夹 = 一组标签。列表里的 noteCount 是把这些标签关联到的笔记去重后的数量，
 * 所以用户改笔记标签时，文件夹内容会自动变化，不需要再点一次「归档」。</p>
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
        bindTags(userId, folder.getId(), request.tagIds());
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
        bindTags(userId, folderId, request.tagIds());
        return Result.ok(findFolder(userId, folderId));
    }

    @Override
    @Transactional
    public Result removeFolder(long userId, long folderId) {
        requireOwned(userId, folderId);
        // note_folder_tag 的两个外键都是 ON DELETE CASCADE，绑定行自动清掉；
        // 标签与笔记完全不动。
        folderMapper.deleteFolder(folderId, userId);
        return Result.ok(true);
    }

    /* ------------------------------ 内部实现 ------------------------------ */

    /** 文件夹 + 绑定标签 + 笔记数，一次查全，避免每个文件夹再查两次 */
    private List<FolderVo> loadFolders(long userId) {
        List<Map<String, Object>> rows = folderMapper.selectFolders(userId);

        Map<Long, List<Long>> tagIdsByFolder = new HashMap<>();
        Map<Long, List<String>> tagNamesByFolder = new HashMap<>();
        for (Map<String, Object> row : folderMapper.selectFolderTagRows(userId)) {
            Long folderId = toLong(row.get("folder_id"));
            Long tagId = toLong(row.get("tag_id"));
            if (folderId == null || tagId == null) {
                continue;
            }
            tagIdsByFolder.computeIfAbsent(folderId, key -> new ArrayList<>()).add(tagId);
            tagNamesByFolder.computeIfAbsent(folderId, key -> new ArrayList<>())
                    .add((String) row.get("tag_name"));
        }

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
                    tagIdsByFolder.getOrDefault(id, List.of()),
                    tagNamesByFolder.getOrDefault(id, List.of()),
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

    /** 重建绑定：先清空再逐条插入；非本人的标签会被 INSERT ... SELECT 静默忽略 */
    private void bindTags(long userId, long folderId, List<Long> tagIds) {
        folderMapper.deleteFolderTags(folderId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : new LinkedHashSet<>(tagIds)) {
            if (tagId == null) {
                continue;
            }
            folderMapper.insertFolderTag(folderId, tagId, userId);
        }
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
