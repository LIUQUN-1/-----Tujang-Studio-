package com.tujang.studio.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tujang.studio.domain.AppUser;
import com.tujang.studio.domain.UserCredential;

@Mapper
public interface UserMapper {

    String COLUMNS = "id, username, display_name, created_at, last_seen_at";

    @Select("SELECT " + COLUMNS + " FROM app_user WHERE id = #{id}")
    Optional<AppUser> findById(@Param("id") String id);

    /**
     * 按登录名查凭据。
     *
     * <p>库与表的 collation 是 utf8mb4_0900_ai_ci，因此这里的比较**大小写不敏感**：
     * {@code Demo} 与 {@code demo} 是同一个账号，注册时也会被唯一索引挡住。
     * 这是刻意的——否则用户输入大小写不同就登不进去，是更常见的投诉。
     */
    @Select("""
            SELECT id, username, password_hash
            FROM app_user
            WHERE username = #{username}
            """)
    Optional<UserCredential> findCredentialByUsername(@Param("username") String username);

    /**
     * 建账号。**不做幂等**：用户名重复时让唯一索引抛 DuplicateKeyException，
     * 由 AuthService 转成 409。这样"注册重名"在并发下也只有一个人能成功。
     */
    @Insert("""
            INSERT INTO app_user (id, username, password_hash, display_name, created_at, last_seen_at)
            VALUES (#{id}, #{username}, #{passwordHash}, #{displayName}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """)
    int insert(
            @Param("id") String id,
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("displayName") String displayName);

    @Update("UPDATE app_user SET last_seen_at = UTC_TIMESTAMP(3) WHERE id = #{id}")
    int touch(@Param("id") String id);
}
