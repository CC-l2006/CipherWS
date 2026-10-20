-- =============================================================================
-- 迁移脚本：登录改用邮箱（2026-10-20）
--
-- 背景：原来用 username 登录且 username 全库唯一，注册时经常撞名。
--      现在改成 **email 唯一、用邮箱登录**；username 降级为显示名，
--      注册时由邮箱前缀自动生成，不再要求唯一。
--
-- 只对**存量库**执行；全新安装直接跑 note-schema.sql 即可（已是新结构）。
--
-- 用法：
--   mysql -u root -p --default-character-set=utf8mb4 CipherWS < note-migration-2026-10-20.sql
-- =============================================================================

USE `CipherWS`;

-- -----------------------------------------------------------------------------
-- 第 0 步（先自查）：这两种情况会让 ADD UNIQUE 失败，必须先处理
-- -----------------------------------------------------------------------------
-- 0.1 有多少条邮箱为空？email 现在是登录账号，空值无法登录。
SELECT COUNT(*) AS `空邮箱行数` FROM `note_user` WHERE `email` = '' OR `email` IS NULL;

-- 0.2 有没有重复邮箱？（有的话下面第 3 步会报 Duplicate entry）
SELECT `email`, COUNT(*) AS `重复数`
FROM `note_user`
WHERE `email` <> ''
GROUP BY `email`
HAVING COUNT(*) > 1;

-- 如果上面两条都是 0（或没有输出），可以直接往下执行。
-- 若有重复邮箱，先人工确认保留哪个账号，把另一条改成别的邮箱或删掉，例如：
--   SELECT id, username, email FROM `note_user` WHERE `email` = '重复的邮箱';
--   UPDATE `note_user` SET `email` = CONCAT('dup-', id, '-', email) WHERE id = <要改的 id>;
-- 若有空邮箱，补一个唯一值：
--   UPDATE `note_user` SET `email` = CONCAT('legacy-', id, '@local.invalid') WHERE email = '';

-- -----------------------------------------------------------------------------
-- 第 1 步：username 的注释与约束降级
-- -----------------------------------------------------------------------------
-- 去掉 username 的唯一索引（它正是「用户名被占用」的根源）。
-- 注意：如果该索引不存在，这句会报 1091，忽略即可。
ALTER TABLE `note_user` DROP INDEX `uk_user_username`;

-- 给 username 补一个普通索引，后台按显示名搜索还能用上
ALTER TABLE `note_user` ADD INDEX `idx_user_username` (`username`);

ALTER TABLE `note_user`
    MODIFY COLUMN `username` VARCHAR(20) NOT NULL
        COMMENT '显示名，由邮箱前缀生成，不要求唯一',
    MODIFY COLUMN `email` VARCHAR(120) NOT NULL
        COMMENT '登录账号，全库唯一';

-- -----------------------------------------------------------------------------
-- 第 2 步：把历史空邮箱补成合法值（如果第 0.1 步查出有）
--          —— 这里不自动改，交给你按上面注释里的 UPDATE 处理，避免误改
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- 第 3 步：email 变成唯一键
-- -----------------------------------------------------------------------------
ALTER TABLE `note_user` ADD UNIQUE KEY `uk_user_email` (`email`);

-- 旧的 email 普通索引已经没意义（唯一键本身就是索引），存在就删
-- 同样，报 1091 可忽略。
ALTER TABLE `note_user` DROP INDEX `idx_user_email`;

-- -----------------------------------------------------------------------------
-- 验证
-- -----------------------------------------------------------------------------
SHOW CREATE TABLE `note_user`\G

-- 期望看到：
--   UNIQUE KEY `uk_user_email` (`email`)
--   KEY `idx_user_username` (`username`)
-- 且不再有 `uk_user_username`

-- 迁移后：
--   · 登录接口入参从 {username, password} 变成 {email, password}
--   · 注册接口变成 {email, code, password, nickname}
--   · 老账号仍可登录，用户名换成对应的邮箱即可
