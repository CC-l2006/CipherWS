-- =============================================================================
-- CipherWS 笔记 —— 文件夹与「文件夹绑定标签」（旧模型）
--
-- ⚠️ 已被 2026-10-26 的模型取代：文件夹不再绑定标签，笔记直接用 note.folder_id 归入。
--    新库请执行 note-folder-2026-10-26.sql（或直接导入 note-schema.sql）；
--    本脚本只用于还原历史，单独跑完还需要再跑 10-26 迁移。
--
-- 旧设计：文件夹本身不直接装笔记，而是绑定若干标签；
--       一篇笔记只要带有其中任意一个标签，就自动出现在该文件夹里。
--       这样不用维护 note-folder 关联，改标签即改归类。
--
-- 用法（存量库）：
--   mysql -u root -p < server/sql/note-folder-2026-10-25.sql
-- 可重复执行。
-- =============================================================================

USE `CipherWS`;
SET NAMES utf8mb4;

-- 1. 文件夹：用户私有，唯一键 (user_id, name)
CREATE TABLE IF NOT EXISTS `note_folder` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '文件夹 ID',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
    `name`       VARCHAR(20) NOT NULL COMMENT '文件夹名，最多 20 字符',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_folder_user_name` (`user_id`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记文件夹表';

-- 2. 文件夹-标签绑定：一个文件夹可绑多个标签，一个标签也可被多个文件夹使用
--    两个外键都是 ON DELETE CASCADE：删文件夹 / 删标签都会自动解除绑定。
CREATE TABLE IF NOT EXISTS `note_folder_tag` (
    `folder_id` BIGINT NOT NULL,
    `tag_id`    BIGINT NOT NULL,
    PRIMARY KEY (`folder_id`, `tag_id`),
    KEY `idx_folder_tag_tag` (`tag_id`),
    CONSTRAINT `fk_folder_tag_folder` FOREIGN KEY (`folder_id`) REFERENCES `note_folder` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_folder_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `note_tag` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件夹-标签绑定表';
