-- 给 delivery_record 补充「已完成测评」标记
-- 适用 MySQL 5.7+；幂等：列已存在则自动跳过，可重复执行
-- 用法：先 USE 目标库，再 SOURCE 本文件
--   mysql -u root -p jobseeker < add_delivery_exam_done.sql

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

-- 已完成测评：勾选后不再显示测评截止时间倒计时，也不参与「即将截止」提醒
CALL add_column_if_missing('delivery_record', 'exam_done', 'TINYINT(1) DEFAULT 0');

DROP PROCEDURE add_column_if_missing;
