package com.tujang.studio.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 登录会话。库里只存 token 的 SHA-256，不存原始值。 */
@Mapper
public interface SessionMapper {

    /**
     * 开一个会话。过期时间由数据库算（30 天），避免应用与数据库时钟口径不一致。
     *
     * <p>{@code ON DUPLICATE KEY UPDATE}：token 是 32 字节随机数，撞的概率可以忽略，
     * 但真撞上时宁可复用同一行为空会话，也不要 500。
     */
    @Insert("""
            INSERT INTO user_session (token_hash, user_id, created_at, expires_at, last_seen_at)
            VALUES (#{tokenHash}, #{userId}, UTC_TIMESTAMP(3),
                    DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 30 DAY), UTC_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), expires_at = VALUES(expires_at)
            """)
    int insert(@Param("tokenHash") String tokenHash, @Param("userId") String userId);

    /** 有效（未过期）会话对应的用户 id。过期的行由 {@link #deleteExpired()} 清理。 */
    @Select("""
            SELECT user_id
            FROM user_session
            WHERE token_hash = #{tokenHash} AND expires_at > UTC_TIMESTAMP(3)
            """)
    Optional<String> findUserId(@Param("tokenHash") String tokenHash);

    @Delete("DELETE FROM user_session WHERE token_hash = #{tokenHash}")
    int delete(@Param("tokenHash") String tokenHash);

    /** 顺带清掉过期会话；登录时调用一次即可，不必单独起定时任务。 */
    @Delete("DELETE FROM user_session WHERE expires_at <= UTC_TIMESTAMP(3)")
    int deleteExpired();
}
