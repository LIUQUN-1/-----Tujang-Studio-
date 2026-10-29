package com.tujang.studio.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.tujang.studio.domain.AppUser;
import com.tujang.studio.mapper.UserMapper;

/**
 * 极简身份：Cookie 里的值就是 {@code app_user.id}。
 *
 * <p>规则（README 有完整说明）：
 * <ul>
 *   <li>Cookie 存在且库里能找到 → 就是这个人；</li>
 *   <li>Cookie 是合法 UUID 但库里没有 → **采用它并建行**（前端中间件预先生成，避免首屏多建一个空用户）；</li>
 *   <li>没有 Cookie 或格式不对 → 服务端新建一个，并把新 id 通过 Set-Cookie 发回去。</li>
 * </ul>
 *
 * <p>这不是认证：知道对方 UUID 就能看到对方的记录。本题不要求登录体系，
 * 这里刻意保持"身份 = 随机不可猜的 UUID"这一层，且在 README 里写明边界。
 */
@Service
public class UserService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    /** 每个请求都写 last_seen 没必要，超过这个间隔才更新。 */
    private static final int TOUCH_AFTER_MINUTES = 10;

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** @param issuedCookie 是否需要在响应里 Set-Cookie（新建或纠正了非法 Cookie） */
    public record Resolution(AppUser user, boolean issuedCookie) {
    }

    /**
     * 解析身份。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：首屏会有并发请求同时来"认领"同一个 Cookie。
     * 如果包在一个事务里，MySQL 默认的 REPEATABLE READ 会让"插入后再查"仍读到事务开始时的旧快照，
     * 导致刚被别的线程写进去的行查不到、抛"写入后读不到"。
     * 让每条语句各自提交，读到的就是最新已提交状态；插入本身已是幂等（ON DUPLICATE KEY UPDATE）。
     */
    public Resolution resolveOrCreate(String cookieValue) {
        if (cookieValue != null && UUID_PATTERN.matcher(cookieValue).matches()) {
            Optional<AppUser> existing = userMapper.findById(cookieValue);
            if (existing.isPresent()) {
                AppUser user = existing.get();
                maybeTouch(user);
                return new Resolution(user, false);
            }
            // 中间件预生成的 id：直接采用，避免首屏多一个空用户
            return new Resolution(insertWith(cookieValue), false);
        }

        AppUser created = insertWith(UUID.randomUUID().toString());
        return new Resolution(created, true);
    }

    /** 演示用：直接换一个新用户。 */
    public AppUser createNew() {
        return insertWith(UUID.randomUUID().toString());
    }

    /** 演示用：切回指定用户；目标不存在或格式非法时退化为新建一个。 */
    public AppUser switchTo(String targetUserId) {
        if (targetUserId != null && UUID_PATTERN.matcher(targetUserId).matches()) {
            Optional<AppUser> existing = userMapper.findById(targetUserId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return createNew();
    }

    private AppUser insertWith(String id) {
        userMapper.insert(id, displayNameFor(id));
        return userMapper.findById(id)
                .orElseThrow(() -> new IllegalStateException("新用户写入后读不到：" + id));
    }

    private void maybeTouch(AppUser user) {
        LocalDateTime threshold = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(TOUCH_AFTER_MINUTES);
        if (user.lastSeenAt() == null || user.lastSeenAt().isBefore(threshold)) {
            userMapper.touch(user.id());
        }
    }

    /** "设计师 7F2A"：由 id 前 4 位派生，稳定且便于在演示里区分两个用户。 */
    private static String displayNameFor(String id) {
        String compact = id.replace("-", "");
        return "设计师 " + compact.substring(0, 4).toUpperCase(Locale.ROOT);
    }
}
