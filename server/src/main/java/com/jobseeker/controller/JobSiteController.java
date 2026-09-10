package com.jobseeker.controller;

import com.jobseeker.common.Result;
import com.jobseeker.service.JobSiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job-sites")
@RequiredArgsConstructor
public class JobSiteController {

    private final JobSiteService jobSiteService;

    /** 招聘站点列表（带拼好的外链）。该接口无需登录，便于首页直接展示。 */
    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city) {
        return Result.ok(jobSiteService.list(keyword, city));
    }
}
