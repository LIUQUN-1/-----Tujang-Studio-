-- ---------------------------------------------------------------------------
-- 图匠工坊 Tujang Studio · 数据库结构
-- MySQL 8.0 / utf8mb4
-- 说明：本文件由 docker-entrypoint-initdb.d 在**数据卷为空时**自动执行。
--       手动初始化：mysql --default-character-set=utf8mb4 -u root -p < db/01-schema.sql
-- 时间约定：所有 DATETIME(3) 一律存 UTC，由应用层写入。
-- ---------------------------------------------------------------------------

-- 必须显式声明连接字符集：MySQL 官方镜像的容器里 LANG 为空，
-- mysql 客户端会退化成 latin1，导致脚本里的中文字节被当成 latin1 再转成 utf8mb4，
-- 最终变成"双重编码"的乱码。必须放在任何语句之前。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `tujang`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `tujang`;

-- ---------------------------------------------------------------------------
-- 用户：账号表。登录用 username + 密码（BCrypt 哈希），身份由会话 Cookie 承载。
-- app_user 同时是"记录归属"的外键目标：asset.user_id / task.user_id 指向这里。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `app_user` (
  `id`            CHAR(36)    NOT NULL COMMENT 'UUID',
  `username`      VARCHAR(32) NOT NULL COMMENT '登录名，大小写不敏感（collation 保证）',
  `password_hash` VARCHAR(72) NOT NULL COMMENT 'BCrypt 哈希，永不存明文',
  `display_name`  VARCHAR(64) NOT NULL COMMENT '展示名，默认与 username 相同',
  `created_at`    DATETIME(3) NOT NULL,
  `last_seen_at`  DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB COMMENT '用户账号';

-- ---------------------------------------------------------------------------
-- 会话：登录成功后签发一个随机 token（放进 HttpOnly Cookie），
-- 库里只存它的 SHA-256。这样即使库被读走，也拿不到可用的 Cookie 值。
-- 每次登录一行，退出即删；过期由 expires_at 判定，读取时顺带清理。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_session` (
  `token_hash`   CHAR(64)    NOT NULL COMMENT 'SHA-256(token) 十六进制',
  `user_id`      CHAR(36)    NOT NULL,
  `created_at`   DATETIME(3) NOT NULL,
  `expires_at`   DATETIME(3) NOT NULL,
  `last_seen_at` DATETIME(3) NOT NULL,
  PRIMARY KEY (`token_hash`),
  KEY `idx_session_user` (`user_id`),
  KEY `idx_session_expires` (`expires_at`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT '登录会话';


-- ---------------------------------------------------------------------------
-- 分类：单独成表，保证 Tab 顺序稳定、名称可改
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
  `code`       VARCHAR(32) NOT NULL COMMENT '稳定 code，接口筛选用',
  `name`       VARCHAR(64) NOT NULL COMMENT '展示名',
  `sort_order` INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`code`)
) ENGINE = InnoDB COMMENT '模板分类';

-- ---------------------------------------------------------------------------
-- 模板：制作配方。不是一次执行，只是一份参数说明书。
-- accepted_mime_types 用逗号分隔，避免为 2~3 个值再开一张表。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `template` (
  `id`                   VARCHAR(64)      NOT NULL COMMENT '稳定 slug',
  `title`                VARCHAR(128)     NOT NULL,
  `category_code`        VARCHAR(32)      NOT NULL,
  `cover_url`            VARCHAR(512)     NOT NULL COMMENT '模板封面（相对路径）',
  `description`          VARCHAR(1024)    NOT NULL,
  `required_image_count` TINYINT UNSIGNED NOT NULL COMMENT '需要几张素材',
  `accepted_mime_types`  VARCHAR(255)     NOT NULL COMMENT '逗号分隔，如 image/jpeg,image/png',
  `max_file_size_bytes`  BIGINT UNSIGNED  NOT NULL,
  `sort_order`           INT              NOT NULL DEFAULT 0,
  `created_at`           DATETIME(3)      NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_template_category` (`category_code`, `sort_order`),
  CONSTRAINT `fk_template_category` FOREIGN KEY (`category_code`) REFERENCES `category` (`code`)
) ENGINE = InnoDB COMMENT '模板（制作配方）';

-- ---------------------------------------------------------------------------
-- 素材：用户上传的输入图片。文件在磁盘，表里只存路径+元信息。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `asset` (
  `id`                CHAR(26)        NOT NULL COMMENT 'ULID',
  `user_id`           CHAR(36)        NOT NULL,
  `template_id`       VARCHAR(64)     NULL COMMENT '上传时带的模板（可空）',
  `stored_path`       VARCHAR(512)    NOT NULL COMMENT '相对 storage root 的路径',
  `original_filename` VARCHAR(255)    NOT NULL,
  `mime_type`         VARCHAR(64)     NOT NULL,
  `size_bytes`        BIGINT UNSIGNED NOT NULL,
  `width`             INT UNSIGNED    NULL,
  `height`            INT UNSIGNED    NULL,
  `created_at`        DATETIME(3)     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_asset_user_created` (`user_id`, `created_at`),
  CONSTRAINT `fk_asset_user`     FOREIGN KEY (`user_id`)     REFERENCES `app_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_asset_template` FOREIGN KEY (`template_id`) REFERENCES `template` (`id`)
) ENGINE = InnoDB COMMENT '用户上传素材';

-- ---------------------------------------------------------------------------
-- 任务：一次执行实例。status 与 current_step 二维正交。
-- sim_* 两列是"受理时快照"的模拟配置，保证并发任务各按自己的剧本推进。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task` (
  `id`             CHAR(26)        NOT NULL COMMENT 'ULID',
  `user_id`        CHAR(36)        NOT NULL,
  `template_id`    VARCHAR(64)     NOT NULL,
  `status`         VARCHAR(16)     NOT NULL COMMENT 'PENDING|PROCESSING|SUCCEEDED|FAILED',
  `current_step`   VARCHAR(16)     NULL COMMENT 'ASSET_CHECK|GENERATE|FINALIZE',
  `failed_step`    VARCHAR(16)     NULL COMMENT '仅 FAILED 有值',
  `error_code`     VARCHAR(64)     NULL,
  `error_message`  VARCHAR(512)    NULL,
  `result_url`     VARCHAR(512)    NULL COMMENT '成功后才有',
  `result_width`   INT UNSIGNED    NULL,
  `result_height`  INT UNSIGNED    NULL,
  `sim_fail_step`  VARCHAR(16)     NULL COMMENT 'dev：本任务注入失败的位置',
  `sim_durations`  VARCHAR(255)    NULL COMMENT 'dev：JSON，各步耗时毫秒',
  `created_at`     DATETIME(3)     NOT NULL,
  `started_at`     DATETIME(3)     NULL,
  `finished_at`    DATETIME(3)     NULL,
  `updated_at`     DATETIME(3)     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_user_created` (`user_id`, `created_at`),
  KEY `idx_task_status` (`status`),
  CONSTRAINT `fk_task_user`     FOREIGN KEY (`user_id`)     REFERENCES `app_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_task_template` FOREIGN KEY (`template_id`) REFERENCES `template` (`id`),
  CONSTRAINT `ck_task_status`
    CHECK (`status` IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
  CONSTRAINT `ck_task_step`
    CHECK (`current_step` IS NULL OR `current_step` IN ('ASSET_CHECK', 'GENERATE', 'FINALIZE')),
  CONSTRAINT `ck_task_success_has_result`
    CHECK (`status` <> 'SUCCEEDED' OR `result_url` IS NOT NULL),
  CONSTRAINT `ck_task_fail_has_step`
    CHECK (`status` <> 'FAILED' OR `failed_step` IS NOT NULL)
) ENGINE = InnoDB COMMENT '制作任务';

-- ---------------------------------------------------------------------------
-- 任务-素材：多对多，position 保证顺序（第 1 张/第 2 张）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_asset` (
  `task_id`  CHAR(26)        NOT NULL,
  `asset_id` CHAR(26)        NOT NULL,
  `position` TINYINT UNSIGNED NOT NULL COMMENT '从 0 开始',
  PRIMARY KEY (`task_id`, `asset_id`),
  UNIQUE KEY `uk_task_position` (`task_id`, `position`),
  CONSTRAINT `fk_ta_task`  FOREIGN KEY (`task_id`)  REFERENCES `task` (`id`)  ON DELETE CASCADE,
  CONSTRAINT `fk_ta_asset` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`)
) ENGINE = InnoDB COMMENT '任务素材关联';
