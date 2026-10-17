-- V3：给 delivery_record 补充「测评截止时间」与「投递网址」两列
--
-- 命名说明（这里踩过一个坑，值得写下来）：
--   原脚本文件名是 add_delivery_deadline_url.sql，但它实际加的两列是
--   exam_deadline 与 apply_url，**文件名与列名根本不一致**。
--   手工迁移时代只能靠"逐个查列"确认执行状态（本次盘点就是这么做的，
--   一度因为按文件名猜列名而误判成"没执行"）。
--   Flyway 时代文件名带版本与描述，且执行状态记在 flyway_schema_history，
--   不再需要靠猜。
--
-- 与原脚本的等价改写（同 V2）：去掉 USE / DELIMITER / 存储过程。

-- 截止时间：精确到分钟，格式 YYYY-MM-DD HH:mm（与项目既有 VARCHAR 日期字段风格一致）
SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE delivery_record ADD COLUMN exam_deadline VARCHAR(16) NULL',
        'DO 0'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_record'
      AND COLUMN_NAME = 'exam_deadline'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 投递网址：仅允许 http/https，写入前由 DeliveryService 校验
SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE delivery_record ADD COLUMN apply_url VARCHAR(512) NULL',
        'DO 0'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_record'
      AND COLUMN_NAME = 'apply_url'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;