package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历主表。Agent 只产出内容，文件资产由本服务管理。
 */
@Data
@TableName("resume")
public class Resume {

    /** upload=上传文件 / paste=粘贴文本 / ai=从 AI 产物导入 */
    public static final String SOURCE_UPLOAD = "upload";
    public static final String SOURCE_PASTE = "paste";
    public static final String SOURCE_AI = "ai";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String name;

    /** 存储路径（相对 StorageProperties.baseDir），paste 类型为空 */
    private String filePath;

    private String sourceType;

    /** 简历正文（paste 类型直接存文本） */
    private String content;

    /** 标签：应届生 / 职场新人 / 资深专家 */
    private String tag;

    private Long positionId;

    /** draft=待优化 / optimized=已优化 */
    private String status;

    private LocalDateTime createdAt;
}
