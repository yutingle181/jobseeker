package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.common.BizException;
import com.jobseeker.dto.AuthRequest;
import com.jobseeker.entity.User;
import com.jobseeker.mapper.UserMapper;
import com.jobseeker.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 账号服务。仅做权限，不含任何会员/计费能力。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public Map<String, Object> register(AuthRequest req) {
        Long cnt = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (cnt != null && cnt > 0) {
            throw new BizException("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPasswordHash(encoder.encode(req.getPassword()));
        u.setNickname(req.getNickname() == null || req.getNickname().isBlank()
                ? req.getUsername() : req.getNickname());
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return token(u);
    }

    public Map<String, Object> login(AuthRequest req) {
        User u = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (u == null || !encoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        return token(u);
    }

    private Map<String, Object> token(User u) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.generate(u.getId()));
        data.put("userId", u.getId());
        data.put("nickname", u.getNickname());
        return data;
    }
}
