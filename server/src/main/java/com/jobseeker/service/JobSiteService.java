package com.jobseeker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobseeker.entity.JobSite;
import com.jobseeker.mapper.JobSiteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 招聘网站快捷入口。
 *
 * 安全：URL 模板只来自数据库的已配置站点（数据库即白名单），
 * 拼接时对参数做 URL 编码并校验协议，杜绝 javascript: 等伪协议与参数注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSiteService {

    private final JobSiteMapper mapper;

    /** 返回站点列表，并把 {keyword} / {city} 替换成实际值。 */
    public List<Map<String, Object>> list(String keyword, String city) {
        List<JobSite> sites = mapper.selectList(
                new LambdaQueryWrapper<JobSite>()
                        .eq(JobSite::getEnabled, true)
                        .orderByAsc(JobSite::getSort));

        List<Map<String, Object>> out = new ArrayList<>();
        for (JobSite s : sites) {
            String url = buildUrl(s.getUrlTemplate(), keyword, city);
            if (url == null) {
                log.warn("招聘站点 URL 不合法，已跳过：{}", s.getName());
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("icon", s.getIcon());
            m.put("url", url);
            out.add(m);
        }
        return out;
    }

    /** 构建外链；协议不合法返回 null（前端则不渲染该卡片）。 */
    String buildUrl(String template, String keyword, String city) {
        if (template == null || template.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(template.split("\\{")[0]);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return null;
            }
            String kw = URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8);
            String ct = URLEncoder.encode(city == null ? "" : city, StandardCharsets.UTF_8);
            return template
                    .replace("{keyword}", kw)
                    .replace("{city}", ct);
        } catch (Exception e) {
            return null;
        }
    }
}
