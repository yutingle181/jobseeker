package com.jobseeker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 招聘网站快捷入口。
 *
 * urlTemplate 形如 https://www.zhipin.com/web/geek/job?query={keyword}&city={city}
 * 出于安全考虑，落库与拼接时都做域名白名单校验，防止开放重定向。
 */
@Data
@TableName("job_site")
public class JobSite {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String icon;

    private String urlTemplate;

    private Integer sort;

    private Boolean enabled;
}
