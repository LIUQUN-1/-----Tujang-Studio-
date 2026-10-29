package com.tujang.studio.web;

import java.time.ZoneOffset;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tujang.studio.domain.AppUser;
import com.tujang.studio.web.dto.MeDto;

import jakarta.servlet.http.HttpServletRequest;

/** 当前身份（docs/api.md §5.1）。 */
@RestController
@RequestMapping("/api")
public class MeController {

    private final Environment environment;

    public MeController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/me")
    public MeDto me(HttpServletRequest request) {
        AppUser user = CurrentUserInterceptor.userOf(request);
        return new MeDto(
                user.id(),
                user.displayName(),
                user.createdAt() == null ? null : user.createdAt().toInstant(ZoneOffset.UTC),
                environment.acceptsProfiles(Profiles.of("dev")));
    }
}
