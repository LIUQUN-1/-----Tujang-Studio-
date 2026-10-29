package com.tujang.studio.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.tujang.studio.mapper.UserMapper;
import com.tujang.studio.service.AuthService;

/**
 * 演示账号：{@code demo / demo1234}（**仅 dev**）。
 *
 * <p>为什么不用 SQL 写死一行：密码哈希带盐，写死在 seed 里既不可校验也不可读；
 * 交给与注册完全相同的 {@link AuthService#register} 创建，行为才和真实注册一致。
 * 生产 profile 没有这个 Bean，也就没有内置账号。
 */
@Component
@Profile("dev")
public class DemoAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);

    public static final String DEMO_USERNAME = "demo";
    public static final String DEMO_PASSWORD = "demo1234";

    private final UserMapper userMapper;
    private final AuthService authService;

    public DemoAccountInitializer(UserMapper userMapper, AuthService authService) {
        this.userMapper = userMapper;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.findCredentialByUsername(DEMO_USERNAME).isPresent()) {
            return;
        }
        try {
            authService.register(DEMO_USERNAME, DEMO_PASSWORD);
            log.info("demo account created: {} / {}", DEMO_USERNAME, DEMO_PASSWORD);
        } catch (RuntimeException e) {
            // 并发启动 / 别人刚好注册了同名账号：都不是错误，记一笔就够
            log.info("demo account not created: {}", e.getMessage());
        }
    }
}
