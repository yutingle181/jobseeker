package com.jobseeker.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * SPA 兜底转发（history 模式刷新/直达子路由用）。
 *
 * 前端用 createWebHistory()，刷新 /jobs、/positions/new 等子路由会直接打后端。
 * 这里把「非接口、非资源」的 GET 路径转发到 index.html，由前端路由接管；
 * 正则排除 /api、/agent（接口）、/icons、/assets（静态资源）以及根级约定文件
 * （/favicon.ico、/favicon.svg、/apple-touch-icon.png），使缺失的静态资源
 * 仍由 ResourceHttpRequestHandler 抛 NoResourceFoundException → 全局处理器返回 404。
 *
 * 站点图标必须一并放行：否则浏览器取 /favicon.ico 会拿到 index.html，
 * HTML 无法当图标解析，标签页只能退化成浏览器默认图标。
 *
 * 注意：RequestMappingHandlerMapping 优先级高于静态资源处理器，故本控制器会先于
 * 资源处理器匹配；被排除的前缀则放行给资源处理器/接口控制器。
 */
@Controller
public class SpaFallbackController {

    @RequestMapping(
            value = {
                    "/{p1:^(?!api|agent|icons|assets|index|favicon|apple-touch-icon).*$}",
                    "/{p1:^(?!api|agent|icons|assets|index|favicon|apple-touch-icon).*$}/{p2}"
            },
            method = RequestMethod.GET)
    public String forwardToIndex() {
        // 转发到根路径（/），由 welcome page 直接返回 index.html。
        // 不能用 forward:/index.html：/index.html 会被本控制器正则再次匹配，造成递归转发 500。
        return "forward:/";
    }
}
