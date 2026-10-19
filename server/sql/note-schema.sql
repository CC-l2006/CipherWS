-- =============================================================================
-- CipherWS 笔记系统 —— MySQL 建表脚本
--
-- 对应 frontend/note/需求文档.md 第 5 节的 9 张表。
-- 字符集统一 utf8mb4（笔记正文会含 emoji 与中日文，utf8 存不下），
-- 存储引擎 InnoDB（需要事务与外键式的一致性）。
--
-- 用法：本脚本**自带建库语句**，直接执行即可（可重复执行，已存在的表与初始数据会跳过）：
--   mysql -u root -p < note-schema.sql
-- 也可以先手工建好库、只导入表结构：
--   mysql -u root -p CipherWS < note-schema.sql
--
-- 注意库名的大小写：Linux 上 MySQL 默认 lower_case_table_names=0，库名**区分大小写**，
-- 所以建库名必须和 JDBC 连接串里的名字完全一致（本项目统一用 CipherWS）。
-- Windows 上默认 lower_case_table_names=1，库名会被强制转成小写 cipherws ——
-- 同一份脚本两端都能跑，但跨平台迁移时别被大小写误导。
--
-- 命名约定：所有表统一以模块名 note 开头（note_user / note_tag / note_ai_* …）。
-- 其中 note 与 note_tag_rel 本身已以 note 开头，不再叠加成 note_note。
-- 好处是同一个库里能容纳多套系统（cipherws 主站、train、note）而互不撞名。
---- 注意：表名 user / note / tag 是需求文档指定的。`note_user` 虽然不是 MySQL 8 的
-- 保留字，但它和 mysql 系统库的 user 表同名，所以下面一律用反引号包裹，
-- 后续所有 SQL 也要这么写。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `CipherWS`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `CipherWS`;

-- 声明「本脚本是按 utf8mb4 发过来的」。
-- 不加这一句的话，客户端若用 latin1/gbk 连接（Windows 上 mysql.exe 的默认
-- 行为取决于系统代码页），下面 `title` 的中文默认值 '无标题笔记' 会被判定为
-- 非法默认值，建表直接失败（ERROR 1067 Invalid default value for 'title'）。
-- 有了它，就不依赖使用者记不记得加 --default-character-set。
SET NAMES utf8mb4;


-- -----------------------------------------------------------------------------
-- 1. note_user 用户表
--    需求 6.2.1：密码必须不可逆加密存储，password 列存 BCrypt 摘要（60 字符），
--    绝不放明文。这里给 100 长度给未来换 Argon2 留余量。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `username`    VARCHAR(20)  NOT NULL COMMENT '账号，2-20 个字符，可用中文/字母/数字/下划线',
    `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要，禁止明文',
    `role`        VARCHAR(16)  NOT NULL DEFAULT 'user' COMMENT '角色：user / admin',
    `enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用，0 禁用后不能登录',
    `email`       VARCHAR(120) NOT NULL DEFAULT '' COMMENT '绑定邮箱，用于找回密码',
    `nickname`    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像地址',
    `bio`         VARCHAR(255) NOT NULL DEFAULT '' COMMENT '个人简介',
    `ai_quota`    INT          NOT NULL DEFAULT 5 COMMENT '剩余 AI 总结次数',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- -----------------------------------------------------------------------------
-- 2. note 笔记表（本身已以 note 开头，不叠加前缀）
--    deleted=1 表示在回收站（需求 4.3.7：支持回收站与永久删除两种模式）。
--    flagged=1 是管理员巡检标记的违规笔记（需求 4.6.2：管理员只标记，不改正文）。
--    正文用 MEDIUMTEXT：一篇笔记可能很长，TEXT 的 64KB 不够。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '笔记 ID',
    `user_id`     BIGINT      NOT NULL COMMENT '所属用户',
    `title`       VARCHAR(80) NOT NULL DEFAULT '无标题笔记' COMMENT '标题',
    `content`     MEDIUMTEXT  NULL COMMENT '正文，富文本/纯文本',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'private' COMMENT '状态：draft 草稿 / private 私有已发布',
    `deleted`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否在回收站',
    `flagged`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '管理员是否标记违规',
    `deleted_at`  DATETIME    NULL COMMENT '进入回收站的时间',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 列表页恒按「用户 + 未删除 + 更新时间倒序」查，这条组合索引直接覆盖
    KEY `idx_note_user_deleted_updated` (`user_id`, `deleted`, `updated_at`),
    -- 管理员巡检按违规筛选
    KEY `idx_note_flagged` (`flagged`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记表';

-- -----------------------------------------------------------------------------
-- 3. note_tag 标签表
--    需求 7.4：标签属于用户私有，不同用户同名标签互不干扰
--    → 唯一约束必须是 (user_id, name) 而不是 name 单列。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_tag` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
    `name`       VARCHAR(12) NOT NULL COMMENT '标签名，最多 12 字符',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_user_name` (`user_id`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标签表';

-- -----------------------------------------------------------------------------
-- 4. note_tag_rel 笔记标签关联表（多对多）
--    需求 4.5.3：删标签只解除关联，不删笔记 —— 所以这里用级联删除关联行是安全的，
--    但不能反过来从关联行去删笔记。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_tag_rel` (
    `note_id` BIGINT NOT NULL,
    `tag_id`  BIGINT NOT NULL,
    PRIMARY KEY (`note_id`, `tag_id`),
    KEY `idx_rel_tag` (`tag_id`),
    CONSTRAINT `fk_rel_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rel_tag` FOREIGN KEY (`tag_id`) REFERENCES `note_tag` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记标签关联表';

-- -----------------------------------------------------------------------------
-- 5. note_ai_summary_record AI 总结记录表
--    需求 4.4.5：同一笔记可多次总结，产生多条记录 → 不加唯一约束。
--    source_snippet 存提交给模型的原文片段，便于事后核对摘要是否失真。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_ai_summary_record` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `note_id`        BIGINT      NOT NULL,
    `user_id`        BIGINT      NOT NULL,
    `source_snippet` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '提交总结的原文片段',
    `summary`        TEXT        NOT NULL COMMENT 'AI 返回的摘要',
    `quota_cost`     INT         NOT NULL DEFAULT 1 COMMENT '本次消耗额度',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_user_created` (`user_id`, `created_at`),
    KEY `idx_ai_note` (`note_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 总结记录表';

-- -----------------------------------------------------------------------------
-- 6. note_system_announcement 系统公告表
--    enabled=0 的公告用户看不到（需求 4.6.4：用户登录后可查看公告）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_system_announcement` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `title`      VARCHAR(80) NOT NULL,
    `content`    TEXT        NOT NULL,
    `admin_id`   BIGINT      NOT NULL COMMENT '发布的管理员',
    `enabled`    TINYINT(1)  NOT NULL DEFAULT 1,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ann_enabled_created` (`enabled`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统公告表';

-- -----------------------------------------------------------------------------
-- 7. note_user_operation_log 用户操作日志表
--    需求 4.7.4：日志不可删除 → 服务层只提供 insert / select，不提供 delete。
--    操作类型：登录 / 新建笔记 / 删除笔记 / AI 总结 / 恢复笔记
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_user_operation_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL,
    `type`       VARCHAR(32)  NOT NULL COMMENT '操作类型',
    `detail`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '操作详情',
    `ip`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '来源 IP',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ulog_user_created` (`user_id`, `created_at`),
    KEY `idx_ulog_type` (`type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户操作日志表';

-- -----------------------------------------------------------------------------
-- 8. note_admin_operation_log 管理员操作日志表
--    操作类型：禁用用户 / 启用用户 / 重置 AI 配额 / 巡检笔记 / 修改 AI 配置 / 发布公告 …
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_admin_operation_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `admin_id`   BIGINT       NOT NULL,
    `type`       VARCHAR(32)  NOT NULL,
    `detail`     VARCHAR(255) NOT NULL DEFAULT '',
    `ip`         VARCHAR(64)  NOT NULL DEFAULT '',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_alog_admin_created` (`admin_id`, `created_at`),
    KEY `idx_alog_type` (`type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员操作日志表';

-- -----------------------------------------------------------------------------
-- 9. note_ai_config AI 配置表
--    需求 6.4.2：AI 接口参数后台可配置，无需改代码重启服务
--    需求 6.2.4：密钥不在前端暴露 → 接口只回传掩码
--    单行配置表：约定 id=1 那行生效（下面有初始化数据）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_ai_config` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT,
    `endpoint`   VARCHAR(255)  NOT NULL COMMENT 'OpenAI 兼容的 chat/completions 地址',
    `api_key`    VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '接口密钥，禁止回传给前端',
    `model`      VARCHAR(64)   NOT NULL DEFAULT 'qwen-plus',
    `max_chars`  INT           NOT NULL DEFAULT 1200 COMMENT '单次总结最大字数',
    `updated_by` BIGINT        NULL COMMENT '最后修改的管理员',
    `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 配置表';

-- =============================================================================
-- 初始化数据
-- =============================================================================

-- AI 配置：占位地址，部署后由管理员在后台改成真实接口
INSERT INTO `note_ai_config` (`id`, `endpoint`, `api_key`, `model`, `max_chars`)
SELECT 1, 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', '', 'qwen-plus', 1200
WHERE NOT EXISTS (SELECT 1 FROM `note_ai_config` WHERE `id` = 1);

-- 管理员账号：admin / admin123
-- 密码摘要是 BCrypt（strength 10）对 "admin123" 的结果，已用
-- BCryptPasswordEncoder.matches("admin123", 该摘要) 实测返回 true。
-- ⚠️ 首次登录后请立刻改密码。
INSERT INTO `note_user` (`username`, `password`, `role`, `enabled`, `email`, `nickname`, `bio`, `ai_quota`)
SELECT 'admin',
       '$2a$10$92FF7YqFlVJUz8/v.orl5.YwxrH0vpsyezysBm1PpmjDBNhu8w4RK',
       'admin', 1, 'admin@cipherws.icu', '站点管理员', '负责用户审核、笔记巡检与 AI 参数配置。', 100
WHERE NOT EXISTS (SELECT 1 FROM `note_user` WHERE `username` = 'admin');

-- 演示账号：demo / demo123（同样实测可匹配）
INSERT INTO `note_user` (`username`, `password`, `role`, `enabled`, `email`, `nickname`, `bio`, `ai_quota`)
SELECT 'demo',
       '$2a$10$Pcp3wsX/qPs3jiPDYVECc.ynQNPebioTtl5Z7rOZY6nvNsSSIlSd6',
       'user', 1, 'demo@cipherws.icu', '池禾', '毕业设计中，用这个笔记本记技术笔记和灵感。', 8
WHERE NOT EXISTS (SELECT 1 FROM `note_user` WHERE `username` = 'demo');

-- 一条欢迎公告
INSERT INTO `note_system_announcement` (`title`, `content`, `admin_id`, `enabled`)
SELECT '笔记系统上线',
       '支持笔记编写、标签分类、回收站与 AI 智能总结。AI 总结每次消耗 1 次额度，额度可在个人资料页查看。',
       (SELECT `id` FROM `note_user` WHERE `username` = 'admin' LIMIT 1), 1
WHERE NOT EXISTS (SELECT 1 FROM `note_system_announcement` WHERE `title` = '笔记系统上线');
