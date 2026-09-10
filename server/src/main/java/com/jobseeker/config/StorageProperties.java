package com.jobseeker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本服务自身的文件存储配置（简历、岗位附件等）。
 * 与 Agent 的 AI 产物目录区分开：本目录可写，Agent 目录只读。
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String baseDir = "./data";
}
