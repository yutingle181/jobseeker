package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档：记录用户上传了哪些文档、归属哪个 Agent 知识库。
 * 建库与问答本身由 Agent 完成，这里只做资产管理。
 */
@Data
@TableName("knowledge_doc")
public class KnowledgeDoc {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** Agent 侧知识库名 */
    private String agentKbName;

    private String fileName;

    /** 源文件在本服务的存储路径 */
    private String filePath;

    private LocalDateTime createdAt;
}
