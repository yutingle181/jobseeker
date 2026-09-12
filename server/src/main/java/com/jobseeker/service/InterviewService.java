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
        // 关键词与 Agent 侧渲染的复盘小标题对齐（interview_review 模式）：
        // 老文案沿用，新文案补上「改进动作 / 薄弱点」，两边都不丢。
        review.setSuggestions(extract(lastAssistant, "建议", "优势", "亮点", "改进动作", "改进建议"));
        review.setWeaknesses(extract(lastAssistant, "弱点", "不足", "待改进", "薄弱点"));
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

    /**
     * 从 Agent 的评价文本里抽取"建议 / 弱点"段落，抽不到则留空由前端展示原文。
     *
     * Agent 的 interview_review 模式产出的是带小标题的 Markdown（`### 薄弱点`、`### 改进动作`），
     * 因此这里按「小标题分段」抽取：命中关键字的标题会把它下面的条目一并带出，遇到下一个标题即结束。
     * 对没有小标题的纯文本（模拟面试的普通点评）自动退化为按行匹配，保持既有行为不变。
     */
    private String extract(String text, String... keys) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> hit = new ArrayList<>();
        boolean inSection = false;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("#")) {
                // 标题行：决定后续条目是否属于本次要抽取的段落
                inSection = containsAny(t, keys);
                if (inSection) {
                    addIfPresent(hit, t);
                }
                continue;
            }
            if (inSection || containsAny(t, keys)) {
                addIfPresent(hit, t);
            }
        }
        return String.join("\n", hit);
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private void addIfPresent(List<String> hit, String line) {
        String item = stripMarkdown(line);
        if (!item.isEmpty()) {
            hit.add(item);
        }
    }

    /** 去掉 Markdown 前缀：标题号、列表符号、任务清单勾选框。 */
    private String stripMarkdown(String line) {
        return line.replaceFirst("^[-*#\\s•]+", "")
                .replaceFirst("^\\[[ xX]\\]\\s*", "")
                .trim();
    }
}
