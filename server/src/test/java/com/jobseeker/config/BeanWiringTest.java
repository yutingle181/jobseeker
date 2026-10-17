package com.jobseeker.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean 装配的静态体检：所有 {@code @Configuration} 类的构造器注入项都必须**是** Spring Bean。
 *
 * <p>为什么要有这条测试（2026-10-16 真机验证时踩到）：
 * {@code RateLimitInterceptor} 被 {@code WebMvcConfig} 用构造器注入，但**漏了
 * {@code @Component}**，导致
 * <pre>
 * APPLICATION FAILED TO START
 * Parameter 1 of constructor in WebMvcConfig required a bean of type
 * 'com.jobseeker.ratelimit.RateLimitInterceptor' that could not be found.
 * </pre>
 * 而当时 {@code mvn test} **44 条全绿** —— 因为那些都是 Mockito 单测，
 * **根本不加载 Spring 上下文**，装配错误一条也测不出来。
 *
 * <p>也正因为如此，"测试全绿"不等于"能启动"。这里做的是**能在单测层面捕获的等价物**：
 * 扫配置类的注入点 → 反查目标类有没有 stereotype 注解。真正兜底的仍然是
 * "每次改完都要起一次服务"，但这条测试能把最常见的那类错误提前到 CI。
 */
class BeanWiringTest {

    private static final Path CONFIG_DIR = Path.of("src", "main", "java", "com", "jobseeker", "config");

    @Test
    @DisplayName("所有 @Configuration 的构造器注入项都必须是 Spring Bean（否则应用起不来）")
    void configurationConstructorInjectionTargetsAreBeans() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Path javaFile : listConfigClasses()) {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            String className = sourceClassName(source);
            Class<?> configClass = loadClass(className);
            if (configClass == null) {
                problems.add(className + "：类名与文件路径不一致，跳过检查");
                continue;
            }
            for (Field field : configClass.getDeclaredFields()) {
                if (!Modifier.isFinal(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                Class<?> target = field.getType();
                if (isFrameworkType(target)) {
                    continue;
                }
                if (!hasBeanAnnotation(target)) {
                    problems.add(String.format(
                            "%s 注入了 %s，但后者没有 @Component/@Service/@Configuration 等注解 —— 应用启动会失败",
                            className, target.getName()));
                }
            }
        }

        assertTrue(problems.isEmpty(),
                "以下注入项不是 Spring Bean（应用会启动失败）：\n  - " + String.join("\n  - ", problems));
    }

    @Test
    @DisplayName("兜底：应用主类存在且带 @SpringBootApplication（否则上下文根本起不来）")
    void applicationClassIsBootstrappable() {
        // 主类叫 JobseekerApplication 而不是 Application —— 一开始凭惯例写成
        // com.jobseeker.Application，用例直接失败才发现。写死类名时要用真实名字 ✓。
        assertTrue(hasAnnotation("com.jobseeker.JobseekerApplication",
                        "org.springframework.boot.autoconfigure.SpringBootApplication"),
                "JobseekerApplication 上应有 @SpringBootApplication");
    }

    @Test
    @DisplayName("同一接口不得有多个 Bean 实现，否则按接口注入会二义（真机起不来）")
    void noAmbiguousInterfaceBeans() {
        // 这条用例是 2026-10-17 真机验证逼出来的，比上面那条更值钱 ✓：
        //   ChatResponseCache 挂着 @Component 且 implements ChatCache，
        //   而 ChatCacheRouter 也 implements ChatCache 且被控制器注入
        //   → 容器里两个 ChatCache bean → AgentGatewayController 按接口注入时
        //     "required a single bean, but 2 were found" → **应用起不来** ✓✗
        // 上一轮 44 条单测全绿、`mvn test` 也全绿，就是这个原因 ✗ ——
        // 所有用例都自己 new 或用 mock，**没有一个真的让 Spring 解析 ChatCache** ✓✗
        //
        // 判据：扫描 cache/lock/ratelimit 三个包里所有"实现了本包内某接口"的类，
        // 若超过一个类实现同一接口，就要求这些类里至多一个带 @Component。
        // （唯一实现方或明确带 @Component 的才允许成为 bean ✓）
        Path[] roots = {
                Path.of("src", "main", "java", "com", "jobseeker", "cache"),
                Path.of("src", "main", "java", "com", "jobseeker", "lock"),
                Path.of("src", "main", "java", "com", "jobseeker", "ratelimit"),
        };
        List<String> problems = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            Map<String, List<Class<?>>> byInterface = new java.util.HashMap<>();
            try (var stream = Files.list(root)) {
                for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(file, StandardCharsets.UTF_8);
                    String className = sourceClassName(src);
                    Class<?> clazz = loadClass(className);
                    if (clazz == null || clazz.isInterface()) {
                        continue;
                    }
                    for (Class<?> itf : clazz.getInterfaces()) {
                        if (itf.getPackageName().startsWith("com.jobseeker")) {
                            byInterface.computeIfAbsent(itf.getName(), k -> new ArrayList<>()).add(clazz);
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("扫描 " + root + " 失败", e);
            }
            for (Map.Entry<String, List<Class<?>>> e : byInterface.entrySet()) {
                List<Class<?>> impls = e.getValue();
                long beanCount = impls.stream().filter(c -> hasBeanAnnotation(c)).count();
                if (impls.size() > 1 && beanCount > 1) {
                    problems.add(String.format(
                            "接口 %s 有 %d 个实现，其中 %d 个带 @Component → 按接口注入会二义，应用起不来",
                            e.getKey(), impls.size(), beanCount));
                }
            }
        }
        assertTrue(problems.isEmpty(),
                "以下接口存在多个 Bean 实现（Spring 无法决定注入哪个）：\n  - "
                        + String.join("\n  - ", problems));
    }

    @Test
    @DisplayName("限流拦截器必须被注册为 Bean（回归：曾漏 @Component 导致启动失败）")
    void rateLimitInterceptorIsRegisteredAsBean() {
        assertTrue(hasAnnotation("com.jobseeker.ratelimit.RateLimitInterceptor", "org.springframework.stereotype.Component"),
                "RateLimitInterceptor 缺少 @Component，WebMvcConfig 注入时会启动失败");
    }

    // ------------------------------------------------------------------ 辅助

    private List<Path> listConfigClasses() throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(CONFIG_DIR)) {
            return out;
        }
        try (var stream = Files.list(CONFIG_DIR)) {
            stream.filter(p -> p.toString().endsWith("Config.java")).forEach(out::add);
        }
        return out;
    }

    private String sourceClassName(String source) {
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (t.startsWith("package ")) {
                String pkg = t.substring("package ".length()).replace(";", "").trim();
                for (String l : source.split("\n")) {
                    String s = l.trim();
                    if (s.startsWith("public class ") || s.startsWith("class ")) {
                        String name = s.substring(s.indexOf("class ") + 6).split("[\\s{<]")[0].trim();
                        return pkg + "." + name;
                    }
                }
            }
        }
        return null;
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isFrameworkType(Class<?> type) {
        String n = type.getName();
        return n.startsWith("java.")
                || n.startsWith("javax.")
                || n.startsWith("jakarta.")
                || type.isPrimitive()
                || type.getName().contains("org.springframework");
    }

    private boolean hasBeanAnnotation(Class<?> target) {
        for (var ann : target.getAnnotations()) {
            String an = ann.annotationType().getName();
            if (an.equals(Component.class.getName())
                    || an.equals(Service.class.getName())
                    || an.equals(Configuration.class.getName())
                    || an.equals(Repository.class.getName())
                    || an.equals(Controller.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(String className, String annotationName) {
        Class<?> c = loadClass(className);
        if (c == null) {
            return false;
        }
        for (var ann : c.getAnnotations()) {
            if (ann.annotationType().getName().equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("配置类目录里确实有东西（防止上面那条因为目录变化而空跑）")
    void configDirIsNotEmpty() throws IOException {
        assertFalse(listConfigClasses().isEmpty(),
                "没扫到任何 *Config.java，说明路径假设变了，这条测试会**假绿**，请同步修正 CONFIG_DIR");
        // 顺手确认源码根目录确实存在（Windows 下相对路径易错）
        assertTrue(Path.of("src", "main", "java").toFile().isDirectory(), "工作目录应为 server/");
    }
}