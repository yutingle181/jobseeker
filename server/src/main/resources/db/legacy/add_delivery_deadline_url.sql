-- 给 delivery_record 补充「截止时间」与「投递网址」两列
-- 适用 MySQL 5.7+；幂等：列已存在则自动跳过，可重复执行
-- 用法：先 USE 目标库，再 SOURCE 本文件
--   mysql -u root -p jobseeker < add_delivery_deadline_url.sql

USE jobseeker;

DELIMITER //

-- 仅当列不存在时才添加
CREATE PROCEDURE add_column_if_missing(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_def    VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('ADDED  COLUMN ', p_column, ' ON ', p_table) AS result;
    ELSE
        SELECT CONCAT('SKIP   COLUMN ', p_column, ' ON ', p_table, ' (already exists)') AS result;
    END IF;
END //

DELIMITER ;

-- 截止时间：精确到分钟，格式 YYYY-MM-DD HH:mm（与项目既有 VARCHAR 日期字段风格一致）
CALL add_column_if_missing('delivery_record', 'exam_deadline', 'VARCHAR(16) NULL');

-- 投递网址：仅允许 http/https，写入前由 DeliveryService 校验
CALL add_column_if_missing('delivery_record', 'apply_url', 'VARCHAR(512) NULL');

DROP PROCEDURE add_column_if_missing;
