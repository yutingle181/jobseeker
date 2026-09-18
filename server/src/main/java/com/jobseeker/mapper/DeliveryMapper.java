package com.jobseeker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobseeker.entity.DeliveryRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeliveryMapper extends BaseMapper<DeliveryRecord> {
}
