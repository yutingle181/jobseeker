package com.jobseeker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 投递记录新增/编辑入参。状态枚举在 Service 层校验。
 */
@Data
public class DeliveryRequest {

    private String companyName;

    @NotBlank(message = "岗位不能为空")
    private String jobTitle;

    private String city;

    private String channel;

    private String deliverDate;

    private String status;

    private String interviewRound;

    private String examInfo;

    /** 截止时间，格式 YYYY-MM-DD HH:mm */
    private String examDeadline;

    /** 是否已完成测评 */
    private Boolean examDone;

    private String lastInterviewTime;

    private String result;

    private String salary;

    private Long positionId;

    private Long resumeId;

    /** 投递网址，仅允许 http/https */
    private String applyUrl;

    private String remark;
}
