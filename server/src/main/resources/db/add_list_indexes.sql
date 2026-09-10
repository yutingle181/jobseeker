-- 给高频列表查询补充 (user_id, created_at) 复合索引，消除 filesort
-- 适用 MySQL 5.7+；幂等：索引已存在则自动跳过，可重复执行
-- 用法：先 USE 目标库，再 SOURCE 本文件
--   mysql -u root -p jobseeker < add_list_indexes.sql

USE jobseeker;

DELIMITER //

-- 仅当索引不存在时才创建
CREATE PROCEDURE add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_def   VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND INDEX_NAME   = p_index
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table, ' ADD INDEX ', p_index, ' ', p_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('ADDED  INDEX ', p_index, ' ON ', p_table) AS result;
    ELSE
        SELECT CONCAT('SKIP   INDEX ', p_index, ' ON ', p_table, ' (already exists)') AS result;
    END IF;
END //

DELIMITER ;

CALL add_index_if_missing('job_position',      'idx_position_user_created',  '(user_id, created_at)');
CALL add_index_if_missing('resume',            'idx_resume_user_created',    '(user_id, created_at)');
CALL add_index_if_missing('interview_session', 'idx_session_user_created',  '(user_id, created_at)');
CALL add_index_if_missing('knowledge_doc',     'idx_kdoc_user_created',      '(user_id, created_at)');
CALL add_index_if_missing('job_search_record', 'idx_jsearch_user_created',   '(user_id, created_at)');

DROP PROCEDURE add_index_if_missing;
