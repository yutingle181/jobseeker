package com.jobseeker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobseeker.entity.JobPosition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobPositionMapper extends BaseMapper<JobPosition> {
}
