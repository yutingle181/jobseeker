package com.jobseeker.controller;

import com.jobseeker.common.Result;
import com.jobseeker.dto.JobSearchSaveRequest;
import com.jobseeker.entity.JobSearchRecord;
import com.jobseeker.service.JobSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/job-searches")
@RequiredArgsConstructor
public class JobSearchController {

    private final JobSearchService jobSearchService;

    @GetMapping
    public Result<List<JobSearchRecord>> list() {
        return Result.ok(jobSearchService.list());
    }

    @PostMapping
    public Result<Long> save(@RequestBody JobSearchSaveRequest req) {
        return Result.ok(jobSearchService.save(req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobSearchService.delete(id);
        return Result.ok();
    }
}
