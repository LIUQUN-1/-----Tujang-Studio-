package com.tujang.studio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希。
 *
 * <p>只引入 {@code spring-security-crypto}（不是整个 Spring Security）：
 * 我们要的只是 BCrypt 实现，不需要过滤器链、不需要 CSRF 那套默认配置。
 *
 * <p>强度 10 是 BCrypt 的默认值：单次校验约几十毫秒，够挡住离线爆破，
 * 又不至于让登录接口变成 DoS 入口。
 */
@Configuration
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
