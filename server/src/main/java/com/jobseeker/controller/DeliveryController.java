package com.jobseeker.controller;

import com.jobseeker.common.Result;
import com.jobseeker.dto.DeliveryRequest;
import com.jobseeker.entity.DeliveryRecord;
import com.jobseeker.service.DeliveryService;
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
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    public Result<List<DeliveryRecord>> list() {
        return Result.ok(deliveryService.list());
    }

    @GetMapping("/stats")
    public Result<DeliveryService.DeliveryStats> stats() {
        return Result.ok(deliveryService.stats());
    }

    @GetMapping("/{id}")
    public Result<DeliveryRecord> detail(@PathVariable Long id) {
        return Result.ok(deliveryService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody DeliveryRequest req) {
        return Result.ok(deliveryService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeliveryRequest req) {
        deliveryService.update(id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deliveryService.delete(id);
        return Result.ok();
    }
}
