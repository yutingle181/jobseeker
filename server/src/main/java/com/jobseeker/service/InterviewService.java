package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.client.AgentClient;
import com.jobseeker.client.dto.AgentMessage;
import com.jobseeker.client.dto.AgentSessionDetail;
import com.jobseeker.common.BizException;
import com.jobseeker.common.UserContext;
import com.jobseeker.dto.ArchiveRequest;
import com.jobseeker.entity.InterviewMessage;
import com.jobseeker.entity.InterviewReview;
import com.jobseeker.entity.InterviewSession;
import com.jobseeker.entity.JobSearchRecord;
import com.jobseeker.mapper.InterviewMessageMapper;
import com.jobseeker.mapper.InterviewReviewMapper;
import com.jobseeker.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试记录与复盘。
 *
 * 数据来自 Agent —— 本服务只做**单向只读归档**：调 Agent 的 /sessions/{id}
 * 把会话拉回来落库，绝不反向写入 Agent。这样两边存储不会互相污染。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final String DISCLAIMER_AI_JOBS =
            "以下为 AI 生成的参考方向，非实时在招职位，请勿当作真实职位投递。";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReviewMapper reviewMapper;
    private final AgentClient agentClient;

    /**
     * 归档：幂等。同一个 agentSessionId 重复归档不会产生重复消息。
     */
    public Long archive(ArchiveRequest req) {
        Long userId = UserContext.require();
        if (req.getAgentSessionId() == null || req.getAgentSessionId().isBlank()) {
            throw new BizException("缺少 agentSessionId");
        }

        InterviewSession exist = sessionMapper.selectOne(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getAgentSessionId, req.getAgentSessionId()));
        if (exist != null) {
            return exist.getId();
        }

        AgentSessionDetail detail = agentClient.fetchSession(req.getAgentSessionId());

        InterviewSession s = new InterviewSession();
        s.setUserId(userId);
        s.setPositionId(req.getPositionId());
        s.setAgentSessionId(detail.getId());
        s.setMode(detail.getMode());
        s.setArtifact(detail.getArtifact());
        s.setFinished(detail.getFinished() != null && detail.getFinished());
        s.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(s);

        List<AgentMessage> msgs = detail.getMessages() == null ? List.of() : detail.getMessages();
        int seq = 0;
        String lastAssistant = "";
        List<InterviewMessage> batch = new ArrayList<>();
        for (AgentMessage m : msgs) {
            InterviewMessage im = new InterviewMessage();
            im.setSessionId(s.getId());
            im.setRole(m.getRole());
            im.setContent(m.getContent());
            im.setSeq(seq++);
            batch.add(im);
            if ("assistant".equalsIgnoreCase(m.getRole())) {
                lastAssistant = m.getContent() == null ? "" : m.getContent();
            }
        }
        if (!batch.isEmpty()) {
            messageMapper.insertBatch(batch);
        }

        InterviewReview review = new InterviewReview();
        review.setSessionId(s.getId());
        review.setUserId(userId);
        review.setSuggestions(extract(lastAssistant, "建议", "优势", "亮点"));
        review.setWeaknesses(extract(lastAssistant, "弱点", "不足", "待改进"));
        review.setDetail(lastAssistant);
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        return s.getId();
    }

    public List<InterviewSession> list() {
        Long userId = UserContext.require();
        return sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt));
    }

    public Map<String, Object> detail(Long id) {
        Long userId = UserContext.require();
        InterviewSession s = sessionMapper.selectById(id);
        if (s == null || !s.getUserId().equals(userId)) {
            throw BizException.notFound("面试记录");
        }
        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, id)
                        .orderByAsc(InterviewMessage::getSeq));
        InterviewReview review = reviewMapper.selectOne(
                new LambdaQueryWrapper<InterviewReview>().eq(InterviewReview::getSessionId, id));

        Map<String, Object> data = new HashMap<>();
        data.put("session", s);
        data.put("messages", messages);
        data.put("review", review);
        return data;
    }

    /** 从 Agent 的评价文本里粗略抽取"建议/弱点"段落，抽不到则留空由前端展示原文。 */
    private String extract(String text, String... keys) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> hit = new ArrayList<>();
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            for (String k : keys) {
                if (t.contains(k)) {
                    hit.add(t.replaceFirst("^[-*#\\s]+", ""));
                    break;
                }
            }
        }
        return String.join("\n", hit);
    }
}
