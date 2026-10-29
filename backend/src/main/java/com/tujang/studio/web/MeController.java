package com.tujang.studio.web;

import java.time.ZoneOffset;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tujang.studio.auth.NotAuthenticatedException;
import com.tujang.studio.domain.AppUser;
import com.tujang.studio.web.dto.MeDto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前身份（docs/api.md §5.1）。
 *
 * <p>未登录直接 401，而不是返回一个"匿名用户"：前端靠这个状态决定显示登录入口还是用户菜单，
 * 语义上"没有人"比"有个匿名的人"更准确。
 */
@RestController
@RequestMapping("/api")
public class MeController {

    private final Environment environment;

    public MeController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/me")
    public MeDto me(HttpServletRequest request) {
        AppUser user = CurrentUserInterceptor.userOrNull(request);
        if (user == null) {
            throw new NotAuthenticatedException();
        }
        return new MeDto(
                user.id(),
                user.username(),
                user.displayName(),
                user.createdAt() == null ? null : user.createdAt().toInstant(ZoneOffset.UTC),
                environment.acceptsProfiles(Profiles.of("dev")));
    }
}
