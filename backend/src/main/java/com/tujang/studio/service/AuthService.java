package com.tujang.studio.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tujang.studio.domain.AppUser;
import com.tujang.studio.domain.UserCredential;
import com.tujang.studio.mapper.SessionMapper;
import com.tujang.studio.mapper.UserMapper;
import com.tujang.studio.support.SessionTokens;
import com.tujang.studio.web.ApiException;

/**
 * 注册 / 登录 / 会话校验 / 退出。
 *
 * <p>几条刻意的选择：
 * <ul>
 *   <li><b>密码只存 BCrypt 哈希</b>，且哈希只在 {@link UserCredential} 里出现；</li>
 *   <li><b>登录失败不区分"用户不存在"和"密码错"</b>——都返回同一句
 *       {@code INVALID_CREDENTIALS}，否则等于送了一个用户名枚举接口；</li>
 *   <li>用户不存在时仍然跑一次 BCrypt 比对（拿一个固定假哈希），
 *       让两条路径的耗时接近，避免用响应时间判断账号是否存在；</li>
 *   <li><b>会话表存 token 的 SHA-256</b>，Cookie 里才是原始 token，
 *       数据库泄露也换不出可用 Cookie。</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 3~24 位字母/数字/下划线。不用邮箱是刻意的：不做邮件验证就不要假装有邮箱。 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,24}$");
    private static final int PASSWORD_MIN = 8;
    /** BCrypt 只看前 72 字节，超长的部分会被静默丢弃，所以直接拒绝。 */
    private static final int PASSWORD_MAX_BYTES = 72;

    /** 用户不存在时用来"陪跑"的哈希（密码是随机串，永远匹配不上）。 */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserMapper userMapper;
    private final SessionMapper sessionMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, SessionMapper sessionMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 登录成功的结果：这个人 + 需要写进 Cookie 的原始 token。 */
    public record SignedIn(AppUser user, String token) {
    }

    public SignedIn register(String username, String password) {
        String name = username == null ? "" : username.trim();
        validateUsername(name);
        validatePassword(password);

        String id = UUID.randomUUID().toString();
        try {
            userMapper.insert(id, name, passwordEncoder.encode(password), name);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("USERNAME_TAKEN", "用户名已被占用，换一个试试");
        }
        AppUser user = userMapper.findById(id)
                .orElseThrow(() -> new IllegalStateException("新账号写入后读不到：" + id));
        log.info("user registered: username={} id={}", user.username(), user.id());
        return new SignedIn(user, openSession(user.id()));
    }

    public SignedIn login(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || password == null || password.isEmpty()) {
            throw invalidCredentials();
        }

        Optional<UserCredential> found = userMapper.findCredentialByUsername(name);
        if (found.isEmpty()) {
            // 陪跑一次比对，让"不存在"与"密码错"耗时接近
            passwordEncoder.matches(password, DUMMY_HASH);
            throw invalidCredentials();
        }
        UserCredential credential = found.get();
        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            throw invalidCredentials();
        }

        AppUser user = userMapper.findById(credential.id())
                .orElseThrow(() -> new IllegalStateException("凭据存在但用户不存在：" + credential.id()));
        sessionMapper.deleteExpired();
        return new SignedIn(user, openSession(user.id()));
    }

    /** 会话 token → 用户。过期或不存在都返回空，不抛异常（拦截器据此放行或匿名）。 */
    public Optional<AppUser> resolveUser(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessionMapper.findUserId(SessionTokens.hash(token))
                .flatMap(userMapper::findById);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionMapper.delete(SessionTokens.hash(token));
        }
    }

    private String openSession(String userId) {
        String token = SessionTokens.newToken();
        sessionMapper.insert(SessionTokens.hash(token), userId);
        userMapper.touch(userId);
        return token;
    }

    private static ApiException invalidCredentials() {
        return new ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "用户名或密码不正确",
                null);
    }

    private static void validateUsername(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw ApiException.invalid("用户名格式不正确",
                    java.util.Map.of("username", "3~24 位字母、数字或下划线"));
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN) {
            throw ApiException.invalid("密码太短",
                    java.util.Map.of("password", "至少 " + PASSWORD_MIN + " 位"));
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw ApiException.invalid("密码太长",
                    java.util.Map.of("password", "最多 " + PASSWORD_MAX_BYTES + " 字节"));
        }
    }
}
