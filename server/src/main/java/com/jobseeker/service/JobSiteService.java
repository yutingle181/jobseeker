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

    /**
     * 招聘站点的城市码映射。仅用于 URL 模板里带 {city} 的站点（目前为 BOSS 直聘）：
     * BOSS 要求传数字城市码（如 长沙=101250100）而非中文城市名，否则打开后无结果。
     * 模板没有 {city}（猎聘/智联/前程无忧）不受影响，始终按关键词搜索。
     */
    private static final Map<String, String> CITY_CODES = new HashMap<>();
    static {
        CITY_CODES.put("北京", "101010100");
        CITY_CODES.put("上海", "101020100");
        CITY_CODES.put("广州", "101280100");
        CITY_CODES.put("深圳", "101280600");
        CITY_CODES.put("杭州", "101210100");
        CITY_CODES.put("成都", "101270100");
        CITY_CODES.put("武汉", "101200100");
        CITY_CODES.put("长沙", "101250100");
        CITY_CODES.put("南京", "101190100");
        CITY_CODES.put("西安", "101110100");
        CITY_CODES.put("苏州", "101190400");
        CITY_CODES.put("重庆", "101040100");
        CITY_CODES.put("天津", "101030100");
        CITY_CODES.put("郑州", "101180100");
        CITY_CODES.put("东莞", "101281600");
        CITY_CODES.put("宁波", "101210400");
        CITY_CODES.put("佛山", "101280800");
        CITY_CODES.put("合肥", "101220100");
        CITY_CODES.put("青岛", "101120100");
        CITY_CODES.put("无锡", "101190200");
        CITY_CODES.put("厦门", "101230200");
        CITY_CODES.put("济南", "101120200");
        CITY_CODES.put("大连", "101070100");
        CITY_CODES.put("沈阳", "101090100");
        CITY_CODES.put("昆明", "101290100");
        CITY_CODES.put("哈尔滨", "101050100");
        CITY_CODES.put("长春", "101060100");
        CITY_CODES.put("福州", "101230100");
        CITY_CODES.put("南昌", "101240100");
        CITY_CODES.put("贵阳", "101260100");
        CITY_CODES.put("南宁", "101300100");
        CITY_CODES.put("石家庄", "101090200");
        CITY_CODES.put("太原", "101080100");
        CITY_CODES.put("兰州", "101160100");
        CITY_CODES.put("海口", "101310100");
        CITY_CODES.put("呼和浩特", "101140100");
        CITY_CODES.put("乌鲁木齐", "101130100");
        CITY_CODES.put("银川", "101170100");
        CITY_CODES.put("西宁", "101150100");
        CITY_CODES.put("拉萨", "101250200");
    }

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

    /**
     * 构建外链；协议不合法返回 null（前端则不渲染该卡片）。
     * 处理 {keyword} 与 {city}：城市码只用于模板带 {city} 的站点（BOSS），
     * 命中码表才带入城市筛选，否则去掉 city 参数段按关键词全国搜索，保证有结果。
     */
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
            String filled = template.replace("{keyword}", kw);

            if (filled.contains("{city}")) {
                String code = resolveCityCode(city);
                if (code != null) {
                    filled = filled.replace("{city}", code);
                } else {
                    // 城市为空或码表未收录：去掉 city 参数段，避免传空串/无效名导致无结果
                    filled = stripCityParam(filled);
                }
            }
            return filled;
        } catch (Exception e) {
            return null;
        }
    }

    /** 中文城市名 → 站点城市码；未收录或为空返回 null。 */
    private String resolveCityCode(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return CITY_CODES.get(city.trim());
    }

    /** 去掉模板里残留的 {city} 参数段（兼容 &city= 与 ?city= 两种形式）。 */
    private String stripCityParam(String t) {
        return t.replaceAll("&city=\\{city\\}", "")
                .replaceAll("\\?city=\\{city\\}", "?");
    }
}
