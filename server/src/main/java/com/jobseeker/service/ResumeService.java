package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.common.BizException;
import com.jobseeker.common.UserContext;
import com.jobseeker.config.AgentProperties;
import com.jobseeker.entity.JobPosition;
import com.jobseeker.entity.Resume;
import com.jobseeker.mapper.JobPositionMapper;
import com.jobseeker.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 简历文件管理。Agent 只产出简历内容，文件资产与版本由本服务管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeMapper mapper;
    private final JobPositionMapper positionMapper;
    private final FileStorageService storage;
    private final AgentProperties agentProps;

    public List<Resume> list(String status) {
        Long userId = UserContext.require();
        LambdaQueryWrapper<Resume> q = new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getCreatedAt);
        if (status != null && !status.isBlank()) {
            q.eq(Resume::getStatus, status);
        }
        return mapper.selectList(q);
    }

    public Resume detail(Long id) {
        Resume r = mapper.selectById(id);
        if (r == null || !r.getUserId().equals(UserContext.require())) {
            throw BizException.notFound("简历");
        }
        return r;
    }

    /** 上传文件建立简历。 */
    public Long upload(MultipartFile file, String name, String tag, Long positionId) {
        String path = storage.save(file, "resume");
        return save(name == null || name.isBlank() ? file.getOriginalFilename() : name,
                path, Resume.SOURCE_UPLOAD, null, tag, positionId, "draft");
    }

    /** 粘贴文本建立简历。 */
    public Long paste(String content, String name, String tag, Long positionId) {
        if (content == null || content.isBlank()) {
            throw new BizException("简历内容不能为空");
        }
        return save(name, null, Resume.SOURCE_PASTE, content, tag, positionId, "draft");
    }

    /**
     * 从 Agent 的 AI 产物目录导入简历（只读读取 Agent_output，不写入）。
     * 这是"简历从 AI 产物导入"的落地：Agent 没有文件下载接口，只有产物路径。
     */
    public Long importFromAi(String fileName, String tag, Long positionId) {
        if (fileName == null || fileName.isBlank()) {
            throw new BizException("产物文件名不能为空");
        }
        Path src = Paths.get(agentProps.getOutputDir(), fileName).normalize();
        if (!Files.exists(src)) {
            throw BizException.notFound("AI 产物：" + fileName);
        }
        try {
            byte[] bytes = Files.readAllBytes(src);
            String path = storage.saveBytes(
                    bytes, "resume", UUID.randomUUID().toString().substring(0, 8) + "-" + fileName);
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return save(fileName, path, Resume.SOURCE_AI, content, tag, positionId, "optimized");
        } catch (IOException e) {
            throw new BizException("导入 AI 产物失败：" + e.getMessage());
        }
    }

    /**
     * 把简历关联到某个岗位（简历管理 ↔ 岗位向导的桥梁）。
     * 先校验简历归属，再校验目标岗位归属当前用户，防 IDOR 越权关联。
     */
    public void linkToPosition(Long resumeId, Long positionId) {
        Resume r = detail(resumeId);
        Long userId = UserContext.require();
        if (positionId != null) {
            JobPosition p = positionMapper.selectOne(new LambdaQueryWrapper<JobPosition>()
                    .eq(JobPosition::getId, positionId)
                    .eq(JobPosition::getUserId, userId));
            if (p == null) {
                throw BizException.notFound("岗位");
            }
        }
        r.setPositionId(positionId);
        mapper.updateById(r);
    }

    /**
     * 落库一份「优化版」简历（内容来自 Agent 生成结果，由前端流式收集后回传）。
     * 状态置为 optimized、来源标记为 ai，使简历管理的「已优化」分类有内容。
     */
    public Long saveOptimized(String content, String name, String tag, Long positionId) {
        if (content == null || content.isBlank()) {
            throw new BizException("优化结果不能为空");
        }
        Long userId = UserContext.require();
        if (positionId != null) {
            JobPosition p = positionMapper.selectOne(new LambdaQueryWrapper<JobPosition>()
                    .eq(JobPosition::getId, positionId)
                    .eq(JobPosition::getUserId, userId));
            if (p == null) {
                throw BizException.notFound("岗位");
            }
        }
        Resume r = new Resume();
        r.setUserId(userId);
        r.setName(name == null || name.isBlank() ? "优化版简历" : name);
        r.setFilePath(null);
        r.setSourceType(Resume.SOURCE_AI);
        r.setContent(content);
        r.setTag(tag);
        r.setPositionId(positionId);
        r.setStatus("optimized");
        r.setCreatedAt(LocalDateTime.now());
        mapper.insert(r);
        return r.getId();
    }

    public void delete(Long id) {
        Resume r = detail(id);
        storage.delete(r.getFilePath());
        mapper.deleteById(id);
    }

    private Long save(String name, String path, String sourceType, String content,
                      String tag, Long positionId, String status) {
        Resume r = new Resume();
        r.setUserId(UserContext.require());
        r.setName(name);
        r.setFilePath(path);
        r.setSourceType(sourceType);
        r.setContent(content);
        r.setTag(tag);
        r.setPositionId(positionId);
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.now());
        mapper.insert(r);
        return r.getId();
    }
}
