package com.tujang.studio;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 图匠工坊 · 素材制作任务后端。
 *
 * <p>职责边界：模板、素材、制作任务、个人记录的全部业务与持久化都在这里。
 * Next.js 只做页面渲染与 /api、/files 的同源转发。
 */
@SpringBootApplication
@EnableAsync
@MapperScan("com.tujang.studio.mapper")
public class TujangStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(TujangStudioApplication.class, args);
    }
}
