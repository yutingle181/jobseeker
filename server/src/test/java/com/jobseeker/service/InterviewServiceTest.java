package com.jobseeker.service;

import com.jobseeker.client.AgentClient;
import com.jobseeker.client.dto.AgentMessage;
import com.jobseeker.client.dto.AgentSessionDetail;
import com.jobseeker.common.UserContext;
import com.jobseeker.dto.ArchiveRequest;
import com.jobseeker.entity.InterviewReview;
import com.jobseeker.entity.JobPosition;
import com.jobseeker.mapper.InterviewMessageMapper;
import com.jobseeker.mapper.InterviewReviewMapper;
import com.jobseeker.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewMessageMapper messageMapper;
    @Mock
    private InterviewReviewMapper reviewMapper;
    @Mock
    private AgentClient agentClient;
    @Mock
    private PositionService positionService;

    @InjectMocks
    private InterviewService interviewService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        UserContext.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private AgentSessionDetail mockInterviewSession() {
        AgentSessionDetail detail = new AgentSessionDetail();
        detail.setId("agent-session-1");
        detail.setMode("mock_interview");
        detail.setFinished(true);
        detail.setMessages(List.of(
                new AgentMessage("user", "请介绍一下你的项目经历"),
                new AgentMessage("assistant", "好的，我做过一个 RAG 系统……"),
                new AgentMessage("user", "这个系统用了什么检索策略？"),
                new AgentMessage("assistant", "用了向量 + BM25 混合检索，并做了重排。")
        ));
        return detail;
    }

    @Test
    void archive_mockInterview_generatesStructuredReview() {
        AgentSessionDetail detail = mockInterviewSession();
        when(agentClient.fetchSession("agent-session-1")).thenReturn(detail);

        String structuredReview = "## 面试复盘：78/100\n整体表现不错。\n"
                + "### 分项得分\n- 技术深度：80/100\n"
                + "### 追问链还原\n1. 项目经历\n2. 检索策略\n"
                + "### 薄弱点\n- 对重排原理回答含糊\n"
                + "### 改进动作\n- [ ] 复盘重排模型";
        when(agentClient.generateReview(any(String.class), any())).thenReturn(structuredReview);

        JobPosition position = new JobPosition();
        position.setTitle("后端工程师");
        position.setDescription("负责检索与 Agent 系统开发。");
        when(positionService.detail(anyLong())).thenReturn(position);

        interviewService.archive(new ArchiveRequest() {{
            setAgentSessionId("agent-session-1");
            setPositionId(100L);
        }});

        // 调用了 Agent 复盘，并注入岗位 JD 作为上下文
        verify(agentClient).generateReview(any(String.class), eq("岗位：后端工程师\nJD：\n负责检索与 Agent 系统开发。"));

        // review 落库内容应为结构化复盘原文
        ArgumentCaptor<InterviewReview> captor = ArgumentCaptor.forClass(InterviewReview.class);
        verify(reviewMapper).insert(captor.capture());
        InterviewReview saved = captor.getValue();
        assertTrue(saved.getDetail().contains("面试复盘：78/100"));
        assertTrue(saved.getDetail().contains("### 薄弱点"));
        // extract 从结构化文本抽出了"改进动作"与"薄弱点"
        assertTrue(saved.getSuggestions().contains("复盘重排模型"));
        assertTrue(saved.getWeaknesses().contains("重排原理回答含糊"));
    }

    @Test
    void archive_mockInterview_reviewFailure_fallsBackToLastAssistant() {
        AgentSessionDetail detail = mockInterviewSession();
        when(agentClient.fetchSession("agent-session-1")).thenReturn(detail);
        // 自动复盘失败
        when(agentClient.generateReview(any(String.class), any()))
                .thenThrow(new com.jobseeker.common.BizException(502, "Agent 不可达"));

        interviewService.archive(new ArchiveRequest() {{
            setAgentSessionId("agent-session-1");
        }});

        ArgumentCaptor<InterviewReview> captor = ArgumentCaptor.forClass(InterviewReview.class);
        verify(reviewMapper).insert(captor.capture());
        // 回退到原始最后一条 assistant 消息
        assertEquals("用了向量 + BM25 混合检索，并做了重排。", captor.getValue().getDetail());
    }

    @Test
    void archive_nonMockInterview_doesNotCallReview() {
        AgentSessionDetail detail = mockInterviewSession();
        detail.setMode("resume");
        when(agentClient.fetchSession("agent-session-1")).thenReturn(detail);

        interviewService.archive(new ArchiveRequest() {{
            setAgentSessionId("agent-session-1");
        }});

        verify(agentClient, never()).generateReview(any(String.class), any());
    }
}
