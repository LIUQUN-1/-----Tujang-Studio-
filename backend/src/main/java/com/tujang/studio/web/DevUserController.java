package com.tujang.studio.web;

import java.time.ZoneOffset;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tujang.studio.auth.CurrentUserHolder;
import com.tujang.studio.domain.AppUser;
import com.tujang.studio.service.UserService;
import com.tujang.studio.support.UserCookie;
import com.tujang.studio.web.dto.MeDto;
import com.tujang.studio.web.dto.SwitchUserRequest;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 演示用：切换身份（**仅 dev**）。
 *
 * <p>不带参数就换成一个全新用户（历史随之清空，用于展示个人记录隔离）；
 * 带上 {@code userId} 可以切回某个已知用户（前端"切回上一个"用）。
 */
@RestController
@RequestMapping("/api/dev/users")
@Profile("dev")
public class DevUserController {

    private final UserService userService;

    public DevUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/switch")
    public MeDto switchUser(
            @RequestBody(required = false) SwitchUserRequest request,
            HttpServletResponse response) {
        AppUser user = userService.switchTo(request == null ? null : request.userId());
        response.addCookie(UserCookie.of(user.id()));
        CurrentUserHolder.set(user.id());
        return new MeDto(
                user.id(),
                user.displayName(),
                user.createdAt() == null ? null : user.createdAt().toInstant(ZoneOffset.UTC),
                true);
    }
}
