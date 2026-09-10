package com.jobseeker.client.dto;

import lombok.Data;

import java.util.List;

/**
 * Agent GET /sessions/{id} 的返回结构 —— 归档的数据来源。
 *
 * 字段与 Python 侧一致：id / mode / kb_name / finished / artifact / messages。
 */
@Data
public class AgentSessionDetail {

    private String id;

    private String mode;

    private String kbName;

    private Boolean finished;

    /** 产物路径字符串（Agent 没有文件下载接口，只是路径） */
    private String artifact;

    private List<AgentMessage> messages;
}
