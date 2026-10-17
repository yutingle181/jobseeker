-- V4：给「按用户 + 时间倒序」的五张列表表补联合索引
--
-- 背景：列表页几乎都是「我的记录，按 created_at 倒序」这一种查询形态，
--   只有单列 user_id 索引时 MySQL 仍需回表排序，数据量上来就慢。
--   (user_id, created_at) 联合索引让它走索引内排序。
--
-- 与原 db/add_list_indexes.sql 的等价改写（同 V2：去掉 USE / DELIMITER / 存储过程）。
-- 已存在则跳过：本机库这 5 个索引都已在（手工执行过）。

-- job_position: idx_position_user_created (user_id, created_at)
SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_position ADD INDEX idx_position_user_created (user_id, created_at)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_position'
      AND INDEX_NAME = 'idx_position_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- resume: idx_resume_user_created (user_id, created_at)
SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE resume ADD INDEX idx_resume_user_created (user_id, created_at)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume'
      AND INDEX_NAME = 'idx_resume_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- interview_session: idx_session_user_created (user_id, created_at)
SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE interview_session ADD INDEX idx_session_user_created (user_id, created_at)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session'
      AND INDEX_NAME = 'idx_session_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- knowledge_doc: idx_kdoc_user_created (user_id, created_at)
SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE knowledge_doc ADD INDEX idx_kdoc_user_created (user_id, created_at)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_doc'
      AND INDEX_NAME = 'idx_kdoc_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- job_search_record: idx_jsearch_user_created (user_id, created_at)
SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_search_record ADD INDEX idx_jsearch_user_created (user_id, created_at)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_search_record'
      AND INDEX_NAME = 'idx_jsearch_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;