package com.jobseeker.client.dto;

import lombok.Data;

/**
 * Agent /sessions/{id} 返回的单条消息。
 */
@Data
public class AgentMessage {

    /** user / assistant */
    private String role;

    private String content;
}
