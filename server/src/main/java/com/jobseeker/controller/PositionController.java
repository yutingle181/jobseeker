package com.jobseeker.controller;

import com.jobseeker.common.Result;
import com.jobseeker.dto.PositionRequest;
import com.jobseeker.entity.JobPosition;
import com.jobseeker.service.PositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public Result<List<JobPosition>> list() {
        return Result.ok(positionService.list());
    }

    @GetMapping("/{id}")
    public Result<JobPosition> detail(@PathVariable Long id) {
        return Result.ok(positionService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody PositionRequest req) {
        return Result.ok(positionService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PositionRequest req) {
        positionService.update(id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.ok();
    }
}
