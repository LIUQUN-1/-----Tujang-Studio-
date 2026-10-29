package com.tujang.studio.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tujang.studio.web.CurrentUserInterceptor;

/**
 * 静态资源映射。
 *
 * <p>三类文件在物理上刻意分开，避免"种子素材被误删"和"运行时产物混进仓库"：
 * <ul>
 *   <li>{@code /files/seed/**} → 仓库内的 seed/（只读，模板封面与预置结果图）</li>
 *   <li>{@code /files/assets/**} → data/assets（用户上传）</li>
 *   <li>{@code /files/results/**} → data/results（制作结果）</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final String dataDir;
    private final String seedDir;
    private final CurrentUserInterceptor currentUserInterceptor;

    public WebConfig(
            @Value("${app.storage.data-dir}") String dataDir,
            @Value("${app.storage.seed-dir}") String seedDir,
            CurrentUserInterceptor currentUserInterceptor) {
        this.dataDir = dataDir;
        this.seedDir = seedDir;
        this.currentUserInterceptor = currentUserInterceptor;
        log.info("storage: data-dir={} seed-dir={}", toUri(dataDir), toUri(seedDir));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/health 不建用户（健康检查会被反复调用，不该污染用户表）；
        // /api/dev/** 是开发工具，也不需要身份（切换用户接口自己建人）。
        registry.addInterceptor(currentUserInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/dev/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/seed/**")
                .addResourceLocations(toUri(seedDir));
        registry.addResourceHandler("/files/assets/**")
                .addResourceLocations(toUri(dataDir + "/assets"));
        registry.addResourceHandler("/files/results/**")
                .addResourceLocations(toUri(dataDir + "/results"));
    }

    /** 转成 Spring 需要的 {@code file:} URL，且必须以 / 结尾。 */
    private static String toUri(String dir) {
        String uri = Paths.get(dir).toAbsolutePath().normalize().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    /** 预留：后续上传/结果目录需要确保存在。 */
    static Path dataPath(String dataDir, String sub) {
        return Paths.get(dataDir).toAbsolutePath().normalize().resolve(sub);
    }
}
