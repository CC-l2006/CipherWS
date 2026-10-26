-- =============================================================================
-- 文件夹模型调整（2026-10-26）
--
-- 旧模型：文件夹绑定标签，笔记靠标签自动归入（note_folder_tag）
-- 新模型：笔记直接有 folder_id；文件夹不绑定标签；
--         点「自动整理」时，把**未分类**笔记按「文件夹名 = 标签名」自动归入。
--
-- 这个脚本会把旧模型里已经归好类的笔记尽量迁移到 folder_id：
--   笔记带有的标签 → 旧 note_folder_tag → 对应的文件夹
-- 迁移完成后删除 note_folder_tag 表。
--
-- 用法（存量库）：
--   mysql -u root -p --default-character-set=utf8mb4 CipherWS < note-folder-2026-10-26.sql
-- 可重复执行。
-- =============================================================================

USE `CipherWS`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `note_folder` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '文件夹 ID',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
    `name`       VARCHAR(20) NOT NULL COMMENT '文件夹名，最多 20 字符',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_folder_user_name` (`user_id`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记文件夹表';

DROP PROCEDURE IF EXISTS `migrate_note_folder`;
DELIMITER $$
CREATE PROCEDURE `migrate_note_folder`()
BEGIN
    -- 1. note.folder_id
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'note'
                     AND COLUMN_NAME = 'folder_id') THEN
        ALTER TABLE `note`
            ADD COLUMN `folder_id` BIGINT NULL COMMENT '所属文件夹，NULL 表示未分类'
                AFTER `user_id`;
    END IF;

    -- 2. 把旧「文件夹-标签」绑定关系换算成 note.folder_id（只补未分类的）
    IF EXISTS (SELECT 1
               FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'note_folder_tag') THEN
        UPDATE `note` n
            JOIN `note_tag_rel` r ON r.`note_id` = n.`id`
            JOIN `note_folder_tag` ft ON ft.`tag_id` = r.`tag_id`
            JOIN `note_folder` f ON f.`id` = ft.`folder_id` AND f.`user_id` = n.`user_id`
        SET n.`folder_id` = f.`id`
        WHERE n.`folder_id` IS NULL;

        DROP TABLE `note_folder_tag`;
    END IF;

    -- 3. 列表筛选与文件夹计数用的联合索引
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'note'
                     AND INDEX_NAME = 'idx_note_folder') THEN
        ALTER TABLE `note`
            ADD INDEX `idx_note_folder` (`user_id`, `folder_id`, `deleted`);
    END IF;
END$$
DELIMITER ;

CALL `migrate_note_folder`();
DROP PROCEDURE `migrate_note_folder`;
