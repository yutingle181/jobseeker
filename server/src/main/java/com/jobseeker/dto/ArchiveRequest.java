package com.jobseeker.dto;

import lombok.Data;

/**
 * 归档请求：把 Agent 的一次会话拉回本服务落库，供复盘使用。
 */
@Data
public class ArchiveRequest {

    /** Agent 侧 session_id（来自 SSE 首个 session 事件） */
    private String agentSessionId;

    /** 可选：关联到本服务的岗位 */
    private Long positionId;
}
