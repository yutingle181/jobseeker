package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.common.UserContext;
import com.jobseeker.entity.JobPosition;
import com.jobseeker.entity.Resume;
import com.jobseeker.mapper.JobPositionMapper;
import com.jobseeker.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户档案装配：把「关联岗位 JD + 关联简历」拼成一段文本，供 Python Agent 消费。
 *
 * 业务数据在 MySQL、AI 编排在 Python 侧，本服务就是两者之间的桥：
 * 只负责**查库 + 拼装文本**，提示词如何消费由 Agent 决定（不在本服务重写 AI 逻辑）。
 *
 * 安全：岗位与简历都必须校验归属当前登录用户，防止越权读取他人 JD/简历（IDOR）。
 * 降级：未传 ID、查不到或校验不通过时返回 null，调用方跳过注入，不阻断对话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentContextService {

    /** JD 截断长度（字符），避免撑爆 prompt */
    private static final int JD_LIMIT = 1200;
    /** 简历正文截断长度（字符） */
    private static final int RESUME_LIMIT = 4000;

    private final JobPositionMapper positionMapper;
    private final ResumeMapper resumeMapper;

    /**
     * 拼装用户档案文本。
     *
     * @param positionId 关联岗位 ID，可空
     * @param resumeId   关联简历 ID，可空
     * @return 用户档案文本；无任何可用关联信息时返回 null（调用方跳过注入）
     */
    public String build(Long positionId, Long resumeId) {
        Long userId = UserContext.require();
        if (positionId == null && resumeId == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【用户档案】以下是该用户在本平台已关联的真实信息，请直接据此回答。\n");
        sb.append("（注意：不要再声称无法访问用户的私人数据；若某项信息缺失，只针对缺失的那一项说明即可。）\n");

        boolean any = false;

        JobPosition position = positionId == null ? null : findPosition(positionId, userId);
        if (position != null) {
            sb.append("\n## 目标岗位\n");
            appendItem(sb, "岗位名称", position.getTitle());
            appendItem(sb, "岗位分类", position.getCategory());
            appendItem(sb, "公司", position.getCompanyName());
            appendItem(sb, "城市", position.getCity());
            appendItem(sb, "岗位描述(JD)", truncate(position.getDescription(), JD_LIMIT));
            any = true;
        }

        Resume resume = resumeId == null ? null : findResume(resumeId, userId);
        if (resume != null) {
            sb.append("\n## 我的简历\n");
            appendItem(sb, "简历名称", resume.getName());
            appendItem(sb, "经验标签", resume.getTag());
            appendItem(sb, "简历正文", truncate(resume.getContent(), RESUME_LIMIT));
            any = true;
        }

        if (!any) {
            log.debug("未找到可用的关联信息，跳过注入 | positionId={} resumeId={}", positionId, resumeId);
            return null;
        }

        // 只记录长度与 ID，不打印 JD / 简历正文，避免敏感信息入日志
        log.debug(
                "已装配用户档案 | userId={} positionId={} resumeId={} 长度={}",
                userId, positionId, resumeId, sb.length());
        return sb.toString();
    }

    /** 按主键 + 当前用户查岗位，查不到即视为无权限或不存在。 */
    private JobPosition findPosition(Long id, Long userId) {
        JobPosition p = positionMapper.selectOne(
                new LambdaQueryWrapper<JobPosition>()
                        .eq(JobPosition::getId, id)
                        .eq(JobPosition::getUserId, userId));
        if (p == null) {
            log.debug("岗位不存在或不属于当前用户，跳过 | positionId={}", id);
        }
        return p;
    }

    /** 按主键 + 当前用户查简历，查不到即视为无权限或不存在。 */
    private Resume findResume(Long id, Long userId) {
        Resume r = resumeMapper.selectOne(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getId, id)
                        .eq(Resume::getUserId, userId));
        if (r == null) {
            log.debug("简历不存在或不属于当前用户，跳过 | resumeId={}", id);
        }
        return r;
    }

    private static void appendItem(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("- ").append(label).append("：").append(value.trim()).append('\n');
    }

    /** 超长文本截断，控制 token 成本。 */
    private static String truncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.length() <= limit) {
            return t;
        }
        return t.substring(0, limit) + "…（已截断）";
    }
}
