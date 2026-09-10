package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.common.BizException;
import com.jobseeker.common.UserContext;
import com.jobseeker.dto.PositionRequest;
import com.jobseeker.entity.JobPosition;
import com.jobseeker.mapper.JobPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位 JD 库：Agent 只把 JD 当作输入使用，存储与多份管理由本服务负责。
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    private final JobPositionMapper mapper;

    public List<JobPosition> list() {
        Long userId = UserContext.require();
        return mapper.selectList(
                new LambdaQueryWrapper<JobPosition>()
                        .eq(JobPosition::getUserId, userId)
                        .orderByDesc(JobPosition::getCreatedAt));
    }

    public JobPosition detail(Long id) {
        JobPosition p = mapper.selectById(id);
        if (p == null || !p.getUserId().equals(UserContext.require())) {
            throw BizException.notFound("岗位");
        }
        return p;
    }

    public Long create(PositionRequest req) {
        JobPosition p = new JobPosition();
        p.setUserId(UserContext.require());
        p.setCategory(req.getCategory());
        p.setTitle(req.getTitle());
        p.setDescription(req.getDescription());
        p.setCompanyName(req.getCompanyName());
        p.setCompanyIntro(req.getCompanyIntro());
        p.setCity(req.getCity());
        p.setCreatedAt(LocalDateTime.now());
        mapper.insert(p);
        return p.getId();
    }

    public void update(Long id, PositionRequest req) {
        JobPosition p = detail(id);
        p.setCategory(req.getCategory());
        p.setTitle(req.getTitle());
        p.setDescription(req.getDescription());
        p.setCompanyName(req.getCompanyName());
        p.setCompanyIntro(req.getCompanyIntro());
        p.setCity(req.getCity());
        mapper.updateById(p);
    }

    public void delete(Long id) {
        detail(id);
        mapper.deleteById(id);
    }
}
