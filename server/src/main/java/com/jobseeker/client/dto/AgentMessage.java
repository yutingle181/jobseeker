package com.jobseeker.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent /sessions/{id} 返回的单条消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    /** user / assistant */
    private String role;

    private String content;
}
