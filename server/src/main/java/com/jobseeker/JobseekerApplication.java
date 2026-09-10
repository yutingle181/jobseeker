package com.jobseeker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 求职辅助软件后端启动类。
 *
 * 定位：本服务只实现 Python Agent 没有的业务能力（账号、岗位、简历、
 * 面试归档与复盘、知识库文档、招聘站点配置），AI 生成能力一律由 Agent 承担，
 * 通过薄网关 /agent/** 透传，本服务不重写任何 Agent 逻辑。
 */
@SpringBootApplication
public class JobseekerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobseekerApplication.class, args);
    }
}
