package com.tujang.studio.web;

import java.time.ZoneOffset;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tujang.studio.auth.CurrentUserHolder;
import com.tujang.studio.domain.AppUser;
import com.tujang.studio.service.AuthService;
import com.tujang.studio.support.SessionCookie;
import com.tujang.studio.web.dto.LoginRequest;
import com.tujang.studio.web.dto.MeDto;
import com.tujang.studio.web.dto.RegisterRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 注册 / 登录 / 退出（docs/api.md §5）。
 *
 * <p>登录成功后：把一个随机 token 写进 HttpOnly Cookie，库里存它的 SHA-256。
 * 之后每个请求由 {@code CurrentUserInterceptor} 反查会话，得到用户。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final Environment environment;

    public AuthController(AuthService authService, Environment environment) {
        this.authService = authService;
        this.environment = environment;
    }

    @PostMapping("/register")
    public ResponseEntity<MeDto> register(
            @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthService.SignedIn signedIn = authService.register(request.username(), request.password());
        response.addCookie(SessionCookie.of(signedIn.token()));
        CurrentUserHolder.set(signedIn.user().id());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(signedIn.user()));
    }

    @PostMapping("/login")
    public MeDto login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.SignedIn signedIn = authService.login(request.username(), request.password());
        response.addCookie(SessionCookie.of(signedIn.token()));
        CurrentUserHolder.set(signedIn.user().id());
        return toDto(signedIn.user());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(SessionCookie.read(request));
        response.addCookie(SessionCookie.cleared());
        return ResponseEntity.noContent().build();
    }

    private MeDto toDto(AppUser user) {
        return new MeDto(
                user.id(),
                user.username(),
                user.displayName(),
                user.createdAt() == null ? null : user.createdAt().toInstant(ZoneOffset.UTC),
                environment.acceptsProfiles(Profiles.of("dev")));
    }
}
