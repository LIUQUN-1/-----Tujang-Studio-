package com.tujang.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tujang.studio.domain.AppUser;
import com.tujang.studio.domain.UserCredential;
import com.tujang.studio.mapper.SessionMapper;
import com.tujang.studio.mapper.UserMapper;
import com.tujang.studio.support.SessionTokens;
import com.tujang.studio.web.ApiException;

/**
 * 注册 / 登录 / 会话校验 / 退出。
 *
 * <p>用可预测的假编码器（{@code ENC(明文)}）而不是真 BCrypt：
 * 这里要测的是"什么时候调用哈希、失败给什么码、库里存的是不是哈希"，
 * BCrypt 本身是库的职责，由容器里的真实登录流程覆盖。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 10, 0);

    @Mock
    private UserMapper userMapper;

    @Mock
    private SessionMapper sessionMapper;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        PasswordEncoder fakeEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "ENC(" + rawPassword + ")";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals(encode(rawPassword));
            }
        };
        authService = new AuthService(userMapper, sessionMapper, fakeEncoder);
    }

    private static AppUser user(String username) {
        return new AppUser(USER_ID, username, username, NOW, NOW);
    }

    @Test
    @DisplayName("注册成功：写 BCrypt 哈希、开一个会话、返回原始 token")
    void registerCreatesUserAndSession() {
        when(userMapper.findById(anyString())).thenReturn(Optional.of(user("alice")));

        AuthService.SignedIn signedIn = authService.register("alice", "secret123");

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).insert(idCaptor.capture(), eq("alice"), hashCaptor.capture(), eq("alice"));
        // 落库的是哈希，不是明文
        assertThat(hashCaptor.getValue()).isEqualTo("ENC(secret123)");
        // 服务端生成的 id 是 UUID，不是用户名
        assertThat(idCaptor.getValue()).matches("[0-9a-f-]{36}").isNotEqualTo("alice");
        assertThat(signedIn.user().username()).isEqualTo("alice");
        assertThat(signedIn.token()).isNotBlank();
    }

    @Test
    @DisplayName("注册时库里存的是 token 的哈希，不是 token 本身")
    void sessionStoresHashNotToken() {
        when(userMapper.findById(anyString())).thenReturn(Optional.of(user("alice")));

        AuthService.SignedIn signedIn = authService.register("alice", "secret123");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(sessionMapper).insert(hashCaptor.capture(), anyString());
        assertThat(hashCaptor.getValue())
                .hasSize(64)
                .isEqualTo(SessionTokens.hash(signedIn.token()))
                .isNotEqualTo(signedIn.token());
    }

    @Test
    @DisplayName("用户名重复：409 USERNAME_TAKEN（靠唯一索引，不靠先查后插）")
    void registerDuplicateUsername() {
        when(userMapper.insert(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("uk_user_username"));

        assertThatThrownBy(() -> authService.register("alice", "secret123"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo("USERNAME_TAKEN");
                });
        verifyNoInteractions(sessionMapper);
    }

    @Test
    @DisplayName("用户名格式不对：400，错误挂在 username 字段上")
    void registerRejectsBadUsername() {
        for (String bad : new String[] {"ab", "a b", "中文名字", "name-with-dash", ""}) {
            assertThatThrownBy(() -> authService.register(bad, "secret123"))
                    .isInstanceOfSatisfying(ApiException.class, ex -> {
                        assertThat(ex.code()).isEqualTo("VALIDATION_FAILED");
                        assertThat(ex.fields()).containsKey("username");
                    });
        }
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("密码太短：400，错误挂在 password 字段上")
    void registerRejectsShortPassword() {
        assertThatThrownBy(() -> authService.register("alice", "1234567"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("VALIDATION_FAILED");
                    assertThat(ex.fields()).containsKey("password");
                });
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("登录成功：返回用户并开新会话")
    void loginSucceeds() {
        when(userMapper.findCredentialByUsername("alice"))
                .thenReturn(Optional.of(new UserCredential(USER_ID, "alice", "ENC(secret123)")));
        when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user("alice")));

        AuthService.SignedIn signedIn = authService.login("alice", "secret123");

        assertThat(signedIn.user().id()).isEqualTo(USER_ID);
        assertThat(signedIn.token()).isNotBlank();
        verify(sessionMapper).insert(anyString(), eq(USER_ID));
    }

    @Test
    @DisplayName("密码错与用户不存在返回同一个错误，不泄露账号是否存在")
    void loginFailuresLookIdentical() {
        when(userMapper.findCredentialByUsername("alice"))
                .thenReturn(Optional.of(new UserCredential(USER_ID, "alice", "ENC(secret123)")));
        when(userMapper.findCredentialByUsername("nobody")).thenReturn(Optional.empty());

        ApiException wrongPassword = catchApiException(() -> authService.login("alice", "wrong-pass"));
        ApiException noSuchUser = catchApiException(() -> authService.login("nobody", "whatever1"));

        assertThat(wrongPassword.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(noSuchUser.code()).isEqualTo(wrongPassword.code());
        assertThat(noSuchUser.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    @DisplayName("用户不存在时仍然跑一次密码比对（让两条路径耗时接近）")
    void loginStillHashesWhenUserMissing() {
        PasswordEncoder countingEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(countingEncoder.matches(any(), anyString())).thenReturn(false);
        when(userMapper.findCredentialByUsername("nobody")).thenReturn(Optional.empty());
        AuthService service = new AuthService(userMapper, sessionMapper, countingEncoder);

        assertThatThrownBy(() -> service.login("nobody", "whatever1"))
                .isInstanceOf(ApiException.class);
        verify(countingEncoder).matches(eq("whatever1"), anyString());
    }

    @Test
    @DisplayName("空用户名或空密码：401，不查库也不比对")
    void loginRejectsEmptyInput() {
        assertThatThrownBy(() -> authService.login("", "secret123"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo("INVALID_CREDENTIALS"));
        assertThatThrownBy(() -> authService.login("alice", ""))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo("INVALID_CREDENTIALS"));
        verify(userMapper, never()).findCredentialByUsername(anyString());
    }

    @Test
    @DisplayName("会话校验：没有 token / 会话过期 / 用户被删都返回空")
    void resolveUserReturnsEmptyWhenSessionMissing() {
        assertThat(authService.resolveUser(null)).isEmpty();
        assertThat(authService.resolveUser("  ")).isEmpty();

        when(sessionMapper.findUserId(SessionTokens.hash("t"))).thenReturn(Optional.empty());
        assertThat(authService.resolveUser("t")).isEmpty();

        when(sessionMapper.findUserId(SessionTokens.hash("t"))).thenReturn(Optional.of(USER_ID));
        when(userMapper.findById(USER_ID)).thenReturn(Optional.empty());
        assertThat(authService.resolveUser("t")).isEmpty();
    }

    @Test
    @DisplayName("会话校验：有效 token 解析出用户")
    void resolveUserReturnsUser() {
        when(sessionMapper.findUserId(SessionTokens.hash("t"))).thenReturn(Optional.of(USER_ID));
        when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user("alice")));

        assertThat(authService.resolveUser("t")).map(AppUser::username).contains("alice");
    }

    @Test
    @DisplayName("退出登录：按 token 的哈希删会话，空 token 不碰库")
    void logoutDeletesSessionByHash() {
        authService.logout("t");
        verify(sessionMapper).delete(SessionTokens.hash("t"));

        authService.logout(null);
        authService.logout("");
        verify(sessionMapper, never()).delete("");
    }

    private static ApiException catchApiException(Runnable action) {
        try {
            action.run();
        } catch (ApiException ex) {
            return ex;
        }
        throw new AssertionError("expected ApiException");
    }
}
