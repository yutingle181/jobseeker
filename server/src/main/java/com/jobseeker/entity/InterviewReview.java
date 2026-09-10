package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 复盘评价：面试结束后由 Agent 的总结消息解析而来。
 */
@Data
@TableName("interview_review")
public class InterviewReview {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long userId;

    /** 建议（可多条，换行分隔） */
    private String suggestions;

    /** 弱点（可多条，换行分隔） */
    private String weaknesses;

    /** 评价详情原文 */
    private String detail;

    private LocalDateTime createdAt;
}
