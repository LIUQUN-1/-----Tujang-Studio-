package com.tujang.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.tujang.studio.domain.AppUser;
import com.tujang.studio.mapper.UserMapper;

/**
 * 身份解析的三条分支：认领已有、采用前端预生成的 id、无 Cookie 时新建。
 *
 * <p>第二条是这套方案的关键——否则首屏 SSR 会多建一个空用户。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    private static final String EXISTING = "0f0c1b3e-0000-4000-8000-000000000001";
    private static final String PRESET = "11111111-2222-4333-8444-555555555555";

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private static AppUser user(String id, LocalDateTime lastSeen) {
        return new AppUser(id, "设计师 ABCD", LocalDateTime.now(ZoneOffset.UTC), lastSeen);
    }

    @Test
    @DisplayName("Cookie 命中已有用户：直接返回，不发新 Cookie")
    void resolvesExistingUser() {
        when(userMapper.findById(EXISTING))
                .thenReturn(Optional.of(user(EXISTING, LocalDateTime.now(ZoneOffset.UTC))));

        var resolution = userService.resolveOrCreate(EXISTING);

        assertThat(resolution.user().id()).isEqualTo(EXISTING);
        assertThat(resolution.issuedCookie()).isFalse();
        verify(userMapper, never()).insert(anyString(), anyString());
    }

    /** 合法 UUID 路径会先查一次"存在吗"：第一次空，插入后再返回刚建的行。 */
    private void givenUserCreatedOnDemand() {
        when(userMapper.findById(anyString()))
                .thenReturn(Optional.empty())
                .thenAnswer(inv -> Optional.of(user(inv.getArgument(0), null)));
    }

    /** 没有 Cookie / Cookie 非法时不会先查库，插入后直接读回。 */
    private void givenInsertAlwaysResolves() {
        when(userMapper.findById(anyString()))
                .thenAnswer(inv -> Optional.of(user(inv.getArgument(0), null)));
    }

    @Test
    @DisplayName("Cookie 是合法 UUID 但库里没有：采用这个 id 建行（不发新 Cookie）")
    void adoptsPresetUuid() {
        givenUserCreatedOnDemand();

        var resolution = userService.resolveOrCreate(PRESET);

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).insert(idCaptor.capture(), anyString());
        assertThat(idCaptor.getValue()).isEqualTo(PRESET);
        assertThat(resolution.user().id()).isEqualTo(PRESET);
        assertThat(resolution.issuedCookie()).isFalse();
    }

    @Test
    @DisplayName("没有 Cookie：新建用户并发新 Cookie")
    void createsWhenNoCookie() {
        givenInsertAlwaysResolves();

        var resolution = userService.resolveOrCreate(null);

        assertThat(resolution.issuedCookie()).isTrue();
        assertThat(resolution.user().id()).matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("Cookie 格式非法：忽略它并新建，避免带着脏值一直 404")
    void replacesMalformedCookie() {
        givenInsertAlwaysResolves();

        var resolution = userService.resolveOrCreate("not-a-uuid");

        assertThat(resolution.issuedCookie()).isTrue();
        assertThat(resolution.user().id()).isNotEqualTo("not-a-uuid");
    }

    @Test
    @DisplayName("显示名由 id 前缀派生，两个用户可区分")
    void displayNameDerivedFromId() {
        givenUserCreatedOnDemand();

        userService.resolveOrCreate(PRESET);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).insert(eq(PRESET), nameCaptor.capture());
        assertThat(nameCaptor.getValue()).isEqualTo("设计师 1111");
    }

    @Test
    @DisplayName("长时间未访问才更新 last_seen，避免每个请求都写库")
    void touchesOnlyWhenStale() {
        LocalDateTime fresh = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        when(userMapper.findById(EXISTING)).thenReturn(Optional.of(user(EXISTING, fresh)));

        userService.resolveOrCreate(EXISTING);
        verify(userMapper, never()).touch(anyString());

        LocalDateTime stale = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
        when(userMapper.findById(EXISTING)).thenReturn(Optional.of(user(EXISTING, stale)));
        userService.resolveOrCreate(EXISTING);
        verify(userMapper).touch(EXISTING);
    }

    @Test
    @DisplayName("切换用户会生成一个全新的 id")
    void createNewGivesFreshIdentity() {
        when(userMapper.findById(anyString()))
                .thenAnswer(inv -> Optional.of(user(inv.getArgument(0), null)));

        AppUser first = userService.createNew();
        AppUser second = userService.createNew();

        assertThat(first.id()).isNotEqualTo(second.id());
    }
}
