package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历版本：保存优化前后的多个版本，便于对比。
 */
@Data
@TableName("resume_version")
public class ResumeVersion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long resumeId;

    private Long userId;

    private String versionName;

    private String content;

    /** 定稿产物文件路径（来自 Agent 或本服务落盘） */
    private String filePath;

    /** 是否已定稿（对应 Agent 的草稿→确认→定稿） */
    private Boolean finalized;

    private LocalDateTime createdAt;
}
