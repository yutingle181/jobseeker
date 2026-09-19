-- 求职辅助软件 · 数据库初始化
-- 说明：只存放 Agent 没有的业务数据；AI 生成内容一律留在 Agent 侧，通过归档单向同步。

CREATE DATABASE IF NOT EXISTS jobseeker
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE jobseeker;

-- 账号（无会员字段）
CREATE TABLE IF NOT EXISTS `user` (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    nickname      VARCHAR(64),
    created_at    DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 岗位 JD
CREATE TABLE IF NOT EXISTS job_position (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    category      VARCHAR(64),
    title         VARCHAR(128) NOT NULL,
    description   TEXT,
    company_name  VARCHAR(128),
    company_intro TEXT,
    city          VARCHAR(64),
    created_at    DATETIME,
    INDEX idx_position_user (user_id),
    INDEX idx_position_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 简历主表
CREATE TABLE IF NOT EXISTS resume (
    id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    name        VARCHAR(255),
    file_path   VARCHAR(512),
    source_type VARCHAR(16),
    content     LONGTEXT,
    tag         VARCHAR(32),
    position_id BIGINT,
    status      VARCHAR(16) DEFAULT 'draft',
    created_at  DATETIME,
    INDEX idx_resume_user (user_id),
    INDEX idx_resume_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 简历版本
CREATE TABLE IF NOT EXISTS resume_version (
    id           BIGINT      PRIMARY KEY AUTO_INCREMENT,
    resume_id    BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    version_name VARCHAR(128),
    content      LONGTEXT,
    file_path    VARCHAR(512),
    finalized    TINYINT(1) DEFAULT 0,
    created_at   DATETIME,
    INDEX idx_version_resume (resume_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 面试会话（归档自 Agent；agent_session_id 唯一保证幂等）
CREATE TABLE IF NOT EXISTS interview_session (
    id               BIGINT      PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    position_id      BIGINT,
    agent_session_id VARCHAR(64) NOT NULL UNIQUE,
    mode             VARCHAR(64),
    artifact         VARCHAR(512),
    finished         TINYINT(1) DEFAULT 0,
    created_at       DATETIME,
    INDEX idx_session_user (user_id),
    INDEX idx_session_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 面试逐条问答
CREATE TABLE IF NOT EXISTS interview_message (
    id         BIGINT      PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT      NOT NULL,
    role       VARCHAR(16),
    content    LONGTEXT,
    seq        INT,
    INDEX idx_message_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 复盘评价
CREATE TABLE IF NOT EXISTS interview_review (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    suggestions TEXT,
    weaknesses  TEXT,
    detail      LONGTEXT,
    created_at  DATETIME,
    INDEX idx_review_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 知识库文档（资产管理；建库与问答在 Agent 侧）
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    agent_kb_name VARCHAR(64) NOT NULL,
    file_name     VARCHAR(255),
    file_path     VARCHAR(512),
    created_at    DATETIME,
    INDEX idx_kdoc_user (user_id),
    INDEX idx_kdoc_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 职位搜索记录（source=agent 的结果为 AI 推断，必须带 disclaimer）
CREATE TABLE IF NOT EXISTS job_search_record (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    position_id BIGINT,
    keyword     VARCHAR(255),
    result      LONGTEXT,
    source      VARCHAR(16) DEFAULT 'agent',
    disclaimer  VARCHAR(255),
    created_at  DATETIME,
    INDEX idx_jsearch_user (user_id),
    INDEX idx_jsearch_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 招聘网站快捷入口（数据库即白名单；url_template 只允许 http/https）
CREATE TABLE IF NOT EXISTS job_site (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name         VARCHAR(64)  NOT NULL,
    icon         VARCHAR(128),
    url_template VARCHAR(512) NOT NULL,
    sort         INT DEFAULT 0,
    enabled      TINYINT(1) DEFAULT 1
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO job_site (name, icon, url_template, sort, enabled) VALUES
    ('BOSS 直聘',  'boss.svg',    'https://www.zhipin.com/web/geek/job?query={keyword}&city={city}', 1, 1),
    ('猎聘',       'liepin.svg',  'https://www.liepin.com/zhaopin/?key={keyword}',                   2, 1),
    ('智联招聘',   'zhaopin.svg', 'https://sou.zhaopin.com/?kw={keyword}',                           3, 1),
    ('前程无忧',   'job51.svg',   'https://we.51job.com/pc/search?keyword={keyword}',                 4, 1)
    ON DUPLICATE KEY UPDATE name = VALUES(name), icon = VALUES(icon);

-- 修正已存在数据（首次初始化后 icon 可能为占位值 briefcase）
UPDATE job_site SET icon = 'boss.svg'    WHERE name = 'BOSS 直聘';
UPDATE job_site SET icon = 'liepin.svg'  WHERE name = '猎聘';
UPDATE job_site SET icon = 'zhaopin.svg' WHERE name = '智联招聘';
UPDATE job_site SET icon = 'job51.svg'   WHERE name = '前程无忧';

-- 秋招投递进度（替代手工 Excel，按用户归属隔离）
CREATE TABLE IF NOT EXISTS delivery_record (
    id                  BIGINT      PRIMARY KEY AUTO_INCREMENT,
    user_id             BIGINT      NOT NULL,
    company_name        VARCHAR(128),
    job_title           VARCHAR(128),
    city                VARCHAR(64),
    channel             VARCHAR(32),
    deliver_date        VARCHAR(10),
    status              VARCHAR(16) DEFAULT '待投递',
    interview_round     VARCHAR(32),
    exam_info           VARCHAR(255),
    exam_deadline       VARCHAR(16),
    exam_done           TINYINT(1) DEFAULT 0,
    last_interview_time VARCHAR(10),
    result              VARCHAR(128),
    salary              VARCHAR(64),
    position_id         BIGINT,
    resume_id           BIGINT,
    apply_url           VARCHAR(512),
    remark              TEXT,
    created_at          DATETIME,
    INDEX idx_delivery_user (user_id),
    INDEX idx_delivery_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
