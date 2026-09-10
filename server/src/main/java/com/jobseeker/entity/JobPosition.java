package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位 JD。Agent 只把 JD 当输入，不管存储，因此由本服务持久化。
 */
@Data
@TableName("job_position")
public class JobPosition {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 岗位分类，如 后端工程师 / 测试工程师 */
    private String category;

    private String title;

    /** 岗位描述（JD 正文，最多 2000 字） */
    private String description;

    private String companyName;

    private String companyIntro;

    /** 城市，用于招聘站点外链拼接 */
    private String city;

    private LocalDateTime createdAt;
}
