package com.jobseeker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobseeker.entity.InterviewMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InterviewMessageMapper extends BaseMapper<InterviewMessage> {

    @Insert({
        "<script>",
        "INSERT INTO interview_message (session_id, role, content, seq) VALUES",
        "<foreach collection='list' item='m' separator=','>",
        "(#{m.sessionId}, #{m.role}, #{m.content}, #{m.seq})",
        "</foreach>",
        "</script>"
    })
    void insertBatch(@Param("list") List<InterviewMessage> list);
}
