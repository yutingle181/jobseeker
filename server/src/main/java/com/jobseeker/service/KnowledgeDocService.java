package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.common.BizException;
import com.jobseeker.common.UserContext;
import com.jobseeker.entity.KnowledgeDoc;
import com.jobseeker.mapper.KnowledgeDocMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库文档管理。
 *
 * 建库与问答由 Agent 完成（走网关 /agent/knowledge/*），本服务只记录
 * 用户上传了哪些文档、归属哪个 Agent 知识库，便于资产管理与界面展示。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocService {

    private final KnowledgeDocMapper mapper;
    private final FileStorageService storage;

    public List<KnowledgeDoc> list() {
        Long userId = UserContext.require();
        return mapper.selectList(
                new LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getUserId, userId)
                        .orderByDesc(KnowledgeDoc::getCreatedAt));
    }

    /** 记录一次上传（真正的建库由前端调网关 /agent/knowledge/{kb}/ingest 完成）。 */
    public Long record(String agentKbName, MultipartFile file) {
        if (agentKbName == null || agentKbName.isBlank()) {
            throw new BizException("知识库名不能为空");
        }
        String path = storage.save(file, "knowledge");
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setUserId(UserContext.require());
        doc.setAgentKbName(agentKbName);
        doc.setFileName(file.getOriginalFilename());
        doc.setFilePath(path);
        doc.setCreatedAt(LocalDateTime.now());
        mapper.insert(doc);
        return doc.getId();
    }

    public void delete(Long id) {
        KnowledgeDoc doc = mapper.selectById(id);
        if (doc == null || !doc.getUserId().equals(UserContext.require())) {
            throw BizException.notFound("文档");
        }
        storage.delete(doc.getFilePath());
        mapper.deleteById(id);
    }
}
