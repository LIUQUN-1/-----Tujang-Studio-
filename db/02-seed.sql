-- ---------------------------------------------------------------------------
-- 图匠工坊 Tujang Studio · 初始化数据
-- 9 个模板 / 4 个分类，刻意覆盖演示所需的三类差异：
--   * required_image_count：1 / 2 / 3 张
--   * accepted_mime_types：pet-sticker 只收 image/jpeg（演示类型校验）
--   * max_file_size_bytes：watercolor-notes 只有 1MB（演示大小校验）
-- 封面图位于 seed/covers/，由 Java 以 /files/seed/** 暴露。
-- ---------------------------------------------------------------------------

-- 同 01-schema.sql：容器内客户端默认 latin1，不显式声明会把中文写坏
SET NAMES utf8mb4;

USE `tujang`;

INSERT INTO `category` (`code`, `name`, `sort_order`) VALUES
  ('creative', '创意图片', 1),
  ('pet',      '宠物',     2),
  ('wedding',  '婚纱',     3),
  ('festival', '节日',     4)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort_order` = VALUES(`sort_order`);

INSERT INTO `template`
  (`id`, `title`, `category_code`, `cover_url`, `description`,
   `required_image_count`, `accepted_mime_types`, `max_file_size_bytes`, `sort_order`, `created_at`)
VALUES
  ('demo-postcard',    '旅行明信片',   'creative', '/files/seed/covers/demo-postcard.jpg',
   '上传两张照片，模拟生成一张带手写体抬头的旅行明信片。', 2, 'image/jpeg,image/png', 5242880, 1, UTC_TIMESTAMP(3)),

  ('oil-portrait',     '油画肖像',     'creative', '/files/seed/covers/oil-portrait.jpg',
   '把一张人像照片做成古典油画质感的肖像作品。', 1, 'image/jpeg,image/png', 5242880, 2, UTC_TIMESTAMP(3)),

  ('watercolor-notes', '水彩手账',     'creative', '/files/seed/covers/watercolor-notes.jpg',
   '用三张照片拼成一张水彩风格的手账页面（单张限 1MB）。', 3, 'image/jpeg,image/png', 1048576, 3, UTC_TIMESTAMP(3)),

  ('pet-avatar',       '萌宠头像',     'pet',      '/files/seed/covers/pet-avatar.jpg',
   '上传一张宠物正脸照，生成一枚圆形萌宠头像。', 1, 'image/jpeg,image/png', 5242880, 4, UTC_TIMESTAMP(3)),

  ('pet-sticker',      '宠物卡通贴纸', 'pet',      '/files/seed/covers/pet-sticker.jpg',
   '用两张照片合成一组卡通贴纸，仅接受 JPG 格式。', 2, 'image/jpeg', 5242880, 5, UTC_TIMESTAMP(3)),

  ('wedding-invite',   '婚礼请柬',     'wedding',  '/files/seed/covers/wedding-invite.jpg',
   '上传两张婚纱照，生成一张竖版婚礼请柬。', 2, 'image/jpeg,image/png', 5242880, 6, UTC_TIMESTAMP(3)),

  ('wedding-portrait', '婚纱写真',     'wedding',  '/files/seed/covers/wedding-portrait.jpg',
   '单张婚纱照，重绘为柔和光线的写真风格。', 1, 'image/jpeg,image/png', 8388608, 7, UTC_TIMESTAMP(3)),

  ('santa-postcard',   '圣诞明信片',   'festival', '/files/seed/covers/santa-postcard.jpg',
   '一张照片配上圣诞元素，生成节日明信片。', 1, 'image/jpeg,image/png', 5242880, 8, UTC_TIMESTAMP(3)),

  ('newyear-poster',   '新年海报',     'festival', '/files/seed/covers/newyear-poster.jpg',
   '三张照片拼成一张新年主题海报。', 3, 'image/jpeg,image/png', 5242880, 9, UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  `title`                = VALUES(`title`),
  `category_code`        = VALUES(`category_code`),
  `cover_url`            = VALUES(`cover_url`),
  `description`          = VALUES(`description`),
  `required_image_count` = VALUES(`required_image_count`),
  `accepted_mime_types`  = VALUES(`accepted_mime_types`),
  `max_file_size_bytes`  = VALUES(`max_file_size_bytes`),
  `sort_order`           = VALUES(`sort_order`);

-- ---------------------------------------------------------------------------
-- 演示用户：固定 UUID，便于在 P1~P5 尚无 Cookie 流程时直接联调；
-- P6 接入 Cookie 后仍可用 POST /api/dev/users/switch 切走再切回。
-- ---------------------------------------------------------------------------
INSERT INTO `app_user` (`id`, `display_name`, `created_at`, `last_seen_at`) VALUES
  ('0f0c1b3e-0000-4000-8000-000000000001', '演示用户', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`);
