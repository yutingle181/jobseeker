package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 面试逐条问答（归档自 Agent 会话消息）。
 */
@Data
@TableName("interview_message")
public class InterviewMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    /** user / assistant */
    private String role;

    private String content;

    private Integer seq;
}
