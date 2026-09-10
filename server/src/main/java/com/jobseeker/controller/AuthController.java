package com.jobseeker.controller;

import com.jobseeker.common.Result;
import com.jobseeker.dto.AuthRequest;
import com.jobseeker.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody AuthRequest req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody AuthRequest req) {
        return Result.ok(authService.login(req));
    }
}
