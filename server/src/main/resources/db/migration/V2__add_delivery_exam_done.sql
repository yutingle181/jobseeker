-- V2：给 delivery_record 补充「已完成测评」标记
--
-- 与原 db/add_delivery_exam_done.sql 的等价改写（内容与列定义逐字一致），
-- 三处必要差异：
--   1) 去掉 USE jobseeker 这一行 —— Flyway 已把会话切到目标库
--   2) 去掉 DELIMITER //      —— 那是 mysql 客户端的指令，Flyway 的解析器不认，
--                                 遇到它会直接报语法错误；
--   3) 去掉 CREATE PROCEDURE  —— 改用 information_schema 判断 + PREPARE/EXECUTE，
--                                 效果与原存储过程相同（"不存在才加"），
--                                 但不依赖 DELIMITER，也不在库里留下函数定义。
--
-- 为什么还需要幂等（Flyway 本身每个版本只跑一次）：
--   已有库在 baseline 之前可能已经手工执行过这个脚本，
--   若不加判断，V2 会在"列已存在"上报 Duplicate column 而让整个应用起不来。
--
-- 业务含义：勾选后不再显示测评截止时间倒计时，也不参与「即将截止」提醒。

-- 仅当列不存在时才添加
SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE delivery_record ADD COLUMN exam_done TINYINT(1) DEFAULT 0',
        'DO 0'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_record'
      AND COLUMN_NAME = 'exam_done'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;