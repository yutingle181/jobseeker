package com.jobseeker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobseeker.entity.JobSearchRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobSearchRecordMapper extends BaseMapper<JobSearchRecord> {
}
