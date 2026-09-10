package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试会话。由「归档」动作从 Agent 的 /sessions/{id} 单向同步而来。
 * agentSessionId 加唯一索引保证幂等。
 */
@Data
@TableName("interview_session")
public class InterviewSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long positionId;

    /** Agent 侧会话 ID，唯一，用于幂等归档 */
    private String agentSessionId;

    /** 模式：mock_interview / interview_questions / ... */
    private String mode;

    /** Agent 侧产物路径（只读参考） */
    private String artifact;

    private Boolean finished;

    private LocalDateTime createdAt;
}
