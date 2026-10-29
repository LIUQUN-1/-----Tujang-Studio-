package com.tujang.studio.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tujang.studio.domain.AppUser;

@Mapper
public interface UserMapper {

    String COLUMNS = "id, display_name, created_at, last_seen_at";

    @Select("SELECT " + COLUMNS + " FROM app_user WHERE id = #{id}")
    Optional<AppUser> findById(@Param("id") String id);

    /**
     * 幂等建用户。
     *
     * <p>{@code ON DUPLICATE KEY UPDATE id = id} 是必须的：首屏会有多个并发请求
     * （layout 取 /api/me、页面取 /api/tasks）同时发现"这个 Cookie 的用户还不存在"，
     * 两边都来插。没有它就会有一个请求撞主键、返回 500。
     */
    @Insert("""
            INSERT INTO app_user (id, display_name, created_at, last_seen_at)
            VALUES (#{id}, #{displayName}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insert(@Param("id") String id, @Param("displayName") String displayName);

    @Update("UPDATE app_user SET last_seen_at = UTC_TIMESTAMP(3) WHERE id = #{id}")
    int touch(@Param("id") String id);
}
