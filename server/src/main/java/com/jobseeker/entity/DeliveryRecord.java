package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秋招投递进度记录。按 user_id 隔离，与已有岗位/简历通过 position_id / resume_id 关联。
 */
@Data
@TableName("delivery_record")
public class DeliveryRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String companyName;

    private String jobTitle;

    private String city;

    private String channel;

    /** 投递日期，格式 YYYY-MM-DD */
    private String deliverDate;

    /** 状态枚举：见 DeliveryService.STATUSES */
    private String status;

    /** 当前面试轮次/阶段，如「二面」「主管面」 */
    private String interviewRound;

    /** 笔试/测评说明 */
    private String examInfo;

    /** 截止时间，格式 YYYY-MM-DD HH:mm（精确到分钟）；为空表示未设置 */
    private String examDeadline;

    /** 最近面试时间，格式 YYYY-MM-DD */
    private String lastInterviewTime;

    /** 结果去向 */
    private String result;

    /** 薪资（Offer） */
    private String salary;

    private Long positionId;

    private Long resumeId;

    /** 投递网址，仅允许 http/https */
    private String applyUrl;

    private String remark;

    private LocalDateTime createdAt;
}
