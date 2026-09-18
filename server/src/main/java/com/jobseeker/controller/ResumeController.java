package com.jobseeker.controller;

import com.jobseeker.common.Result;
import com.jobseeker.entity.Resume;
import com.jobseeker.service.FileStorageService;
import com.jobseeker.service.ResumeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final FileStorageService storage;

    @GetMapping
    public Result<List<Resume>> list(@RequestParam(required = false) String status) {
        return Result.ok(resumeService.list(status));
    }

    @GetMapping("/{id}")
    public Result<Resume> detail(@PathVariable Long id) {
        return Result.ok(resumeService.detail(id));
    }

    @PostMapping("/upload")
    public Result<Long> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) String name,
                               @RequestParam(required = false) String tag,
                               @RequestParam(required = false) Long positionId) {
        return Result.ok(resumeService.upload(file, name, tag, positionId));
    }

    @PostMapping("/paste")
    public Result<Long> paste(@RequestBody PasteResumeReq req) {
        return Result.ok(resumeService.paste(req.getContent(), req.getName(), req.getTag(), req.getPositionId()));
    }

    /** 从 Agent 的 AI 产物目录导入（只读取，不写入 Agent 目录）。 */
    @PostMapping("/import-ai")
    public Result<Long> importFromAi(@RequestParam String fileName,
                                     @RequestParam(required = false) String tag,
                                     @RequestParam(required = false) Long positionId) {
        return Result.ok(resumeService.importFromAi(fileName, tag, positionId));
    }

    /** 把简历关联到岗位（岗位向导第二步选择已有简历时使用）。 */
    @PutMapping("/{id}/position")
    public Result<Void> linkPosition(@PathVariable Long id,
                                     @RequestParam Long positionId) {
        resumeService.linkToPosition(id, positionId);
        return Result.ok();
    }

    /**
     * 落库优化版简历（内容由前端经 SSE 调 Agent 生成后回传）。
     * 仅做存储，不在 Java 侧做任何 AI 编排；优化结果往往较长，
     * 若放在 URL 查询参数里会超过 Tomcat 请求头上限导致 400，故统一走 JSON 请求体。
     */
    @PostMapping("/optimize")
    public Result<Long> optimize(@RequestBody OptimizeResumeReq req) {
        return Result.ok(resumeService.saveOptimized(
                req.getContent(), req.getName(), req.getTag(), req.getPositionId()));
    }

    /** 优化版简历落库请求体。 */
    @Data
    public static class OptimizeResumeReq {
        private String content;
        private String name;
        private String tag;
        private Long positionId;
    }

    /** 粘贴简历请求体（粘贴内容可能较长，故走 body 而非 query）。 */
    @Data
    public static class PasteResumeReq {
        private String content;
        private String name;
        private String tag;
        private Long positionId;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws IOException {
        Resume r = resumeService.detail(id);
        if (r.getFilePath() == null || r.getFilePath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Path path = storage.resolve(r.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        InputStream in = Files.newInputStream(path);
        String encoded = URLEncoder.encode(r.getName() == null ? "resume" : r.getName(),
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.ok();
    }
}
