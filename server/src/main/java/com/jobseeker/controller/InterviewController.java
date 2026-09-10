package com.jobseeker.controller;

import com.jobseeker.common.Result;
import com.jobseeker.dto.ArchiveRequest;
import com.jobseeker.entity.InterviewSession;
import com.jobseeker.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /** 归档：把 Agent 的一次会话拉回本服务，供复盘使用（幂等）。 */
    @PostMapping("/archive")
    public Result<Long> archive(@RequestBody ArchiveRequest req) {
        return Result.ok(interviewService.archive(req));
    }

    @GetMapping
    public Result<List<InterviewSession>> list() {
        return Result.ok(interviewService.list());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(interviewService.detail(id));
    }
}
