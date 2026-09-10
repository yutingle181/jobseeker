package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职位搜索记录。
 *
 * source=agent 表示结果由 AI 助手生成 —— 当前 Agent 的联网检索不可用
 * （ENABLE_WEB_SEARCH=false 且 DuckDuckGo 超时），因此该结果本质是模型推断，
 * 必须随数据返回 disclaimer，前端强制展示"非实时在招职位"。
 */
@Data
@TableName("job_search_record")
public class JobSearchRecord {

    public static final String SOURCE_AGENT = "agent";
    public static final String SOURCE_EXTERNAL = "external";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long positionId;

    /** 检索条件（城市 + 岗位关键词） */
    private String keyword;

    /** 结果正文（Markdown） */
    private String result;

    private String source;

    /** 免责说明，随记录返回给前端展示 */
    private String disclaimer;

    private LocalDateTime createdAt;
}
