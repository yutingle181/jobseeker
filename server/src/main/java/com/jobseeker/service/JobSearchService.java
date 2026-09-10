package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.common.BizException;
import com.jobseeker.common.UserContext;
import com.jobseeker.dto.JobSearchSaveRequest;
import com.jobseeker.entity.JobSearchRecord;
import com.jobseeker.mapper.JobSearchRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位搜索记录。
 *
 * 检索本身由 Agent 完成（本服务不重新实现职位搜索）。
 * 但必须说明：Agent 的联网检索当前不可用（ENABLE_WEB_SEARCH=false 且
 * DuckDuckGo 超时），所以 source=agent 的结果本质是模型推断，
 * 保存时强制带上 disclaimer，前端必须展示，避免用户误当作真实在招职位。
 */
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private static final String DISCLAIMER =
            "以下为 AI 生成的参考方向，非实时在招职位，请勿当作真实职位投递。";

    private final JobSearchRecordMapper mapper;

    public List<JobSearchRecord> list() {
        Long userId = UserContext.require();
        return mapper.selectList(
                new LambdaQueryWrapper<JobSearchRecord>()
                        .eq(JobSearchRecord::getUserId, userId)
                        .orderByDesc(JobSearchRecord::getCreatedAt));
    }

    public Long save(JobSearchSaveRequest req) {
        String source = req.getSource() == null || req.getSource().isBlank()
                ? JobSearchRecord.SOURCE_AGENT : req.getSource();

        JobSearchRecord r = new JobSearchRecord();
        r.setUserId(UserContext.require());
        r.setPositionId(req.getPositionId());
        r.setKeyword(req.getKeyword());
        r.setResult(req.getResult());
        r.setSource(source);
        r.setDisclaimer(JobSearchRecord.SOURCE_AGENT.equals(source) ? DISCLAIMER : "");
        r.setCreatedAt(LocalDateTime.now());
        mapper.insert(r);
        return r.getId();
    }

    public void delete(Long id) {
        JobSearchRecord r = mapper.selectById(id);
        if (r == null || !r.getUserId().equals(UserContext.require())) {
            throw BizException.notFound("搜索记录");
        }
        mapper.deleteById(id);
    }
}
