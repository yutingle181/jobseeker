package com.jobseeker.controller;

import com.jobseeker.common.Result;
import com.jobseeker.entity.KnowledgeDoc;
import com.jobseeker.service.KnowledgeDocService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-docs")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeDocService knowledgeDocService;

    @GetMapping
    public Result<List<KnowledgeDoc>> list() {
        return Result.ok(knowledgeDocService.list());
    }

    @PostMapping
    public Result<Long> record(@RequestParam String agentKbName,
                               @RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeDocService.record(agentKbName, file));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeDocService.delete(id);
        return Result.ok();
    }
}
