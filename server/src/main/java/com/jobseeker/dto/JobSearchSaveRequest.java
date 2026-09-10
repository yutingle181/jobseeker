package com.jobseeker.dto;

import lombok.Data;

@Data
public class JobSearchSaveRequest {

    private Long positionId;

    private String keyword;

    /** 结果正文（Markdown）。source=agent 时由前端把 AI 助手的输出带回来。 */
    private String result;

    /** agent / external */
    private String source;
}
