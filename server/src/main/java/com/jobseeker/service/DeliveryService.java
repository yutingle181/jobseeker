package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.common.BizException;
import com.jobseeker.common.UserContext;
import com.jobseeker.dto.DeliveryRequest;
import com.jobseeker.entity.DeliveryRecord;
import com.jobseeker.mapper.DeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 秋招投递进度：按 user_id 隔离，并提供后端聚合统计。
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    /** 12 种固定状态（前后端共用取值）。 */
    public static final String[] STATUSES = {
        "待投递", "已投递", "简历筛选", "笔试", "测评",
        "一面", "二面", "三面", "HR面", "Offer", "拒信", "放弃"
    };

    private static final Set<String> INTERVIEWING = Set.of("笔试", "测评", "一面", "二面", "三面", "HR面");
    private static final Set<String> REJECTED = Set.of("拒信", "放弃");

    private final DeliveryMapper mapper;

    public List<DeliveryRecord> list() {
        Long userId = UserContext.require();
        return mapper.selectList(
                new LambdaQueryWrapper<DeliveryRecord>()
                        .eq(DeliveryRecord::getUserId, userId)
                        .orderByDesc(DeliveryRecord::getCreatedAt));
    }

    public DeliveryRecord detail(Long id) {
        DeliveryRecord r = mapper.selectById(id);
        if (r == null || !r.getUserId().equals(UserContext.require())) {
            throw BizException.notFound("投递记录");
        }
        return r;
    }

    public Long create(DeliveryRequest req) {
        validateStatus(req.getStatus());
        validateApplyUrl(req.getApplyUrl());
        DeliveryRecord r = new DeliveryRecord();
        r.setUserId(UserContext.require());
        apply(r, req);
        if (r.getStatus() == null) {
            r.setStatus("待投递");
        }
        r.setCreatedAt(LocalDateTime.now());
        mapper.insert(r);
        return r.getId();
    }

    public void update(Long id, DeliveryRequest req) {
        DeliveryRecord r = detail(id);
        validateStatus(req.getStatus());
        validateApplyUrl(req.getApplyUrl());
        apply(r, req);
        mapper.updateById(r);
    }

    public void delete(Long id) {
        detail(id);
        mapper.deleteById(id);
    }

    /** 概览 + 分组聚合，数据量小（秋招数百条内）直接内存计数。 */
    public DeliveryStats stats() {
        List<DeliveryRecord> all = list();
        long total = all.size();
        long offerCount = all.stream().filter(r -> "Offer".equals(r.getStatus())).count();
        long interviewingCount = all.stream().filter(r -> INTERVIEWING.contains(r.getStatus())).count();
        long rejectedCount = all.stream().filter(r -> REJECTED.contains(r.getStatus())).count();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        Arrays.stream(STATUSES).forEach(s -> byStatus.put(s, 0L));
        Map<String, Long> byCity = new HashMap<>();
        Map<String, Long> byChannel = new HashMap<>();
        for (DeliveryRecord r : all) {
            String st = r.getStatus() == null ? "待投递" : r.getStatus();
            byStatus.merge(st, 1L, Long::sum);
            if (r.getCity() != null && !r.getCity().isBlank()) {
                byCity.merge(r.getCity(), 1L, Long::sum);
            }
            if (r.getChannel() != null && !r.getChannel().isBlank()) {
                byChannel.merge(r.getChannel(), 1L, Long::sum);
            }
        }
        return new DeliveryStats(total, offerCount, interviewingCount, rejectedCount, byStatus, byCity, byChannel);
    }

    private void apply(DeliveryRecord r, DeliveryRequest req) {
        r.setCompanyName(req.getCompanyName());
        r.setJobTitle(req.getJobTitle());
        r.setCity(req.getCity());
        r.setChannel(req.getChannel());
        r.setDeliverDate(req.getDeliverDate());
        r.setStatus(req.getStatus());
        r.setInterviewRound(req.getInterviewRound());
        r.setExamInfo(req.getExamInfo());
        r.setExamDeadline(req.getExamDeadline());
        r.setExamDone(Boolean.TRUE.equals(req.getExamDone()));
        r.setLastInterviewTime(req.getLastInterviewTime());
        r.setResult(req.getResult());
        r.setSalary(req.getSalary());
        r.setPositionId(req.getPositionId());
        r.setResumeId(req.getResumeId());
        r.setApplyUrl(req.getApplyUrl());
        r.setRemark(req.getRemark());
    }

    private void validateStatus(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        for (String s : STATUSES) {
            if (s.equals(status)) {
                return;
            }
        }
        throw new BizException(400, "非法的投递状态：" + status);
    }

    /** 投递网址只允许 http/https，避免 javascript:/data: 等被渲染成可点击链接。 */
    private void validateApplyUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String lower = url.trim().toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new BizException(400, "投递网址必须以 http:// 或 https:// 开头");
        }
    }

    /** 统计响应结构。 */
    public record DeliveryStats(
            long total,
            long offerCount,
            long interviewingCount,
            long rejectedCount,
            Map<String, Long> byStatus,
            Map<String, Long> byCity,
            Map<String, Long> byChannel) {
    }
}
