package com.tujang.studio.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tujang.studio.domain.AssetRow;

@Mapper
public interface AssetMapper {

    String COLUMNS = """
            id, user_id, template_id, stored_path, original_filename,
            mime_type, size_bytes, width, height, created_at
            """;

    @Insert("""
            INSERT INTO asset (id, user_id, template_id, stored_path, original_filename,
                               mime_type, size_bytes, width, height, created_at)
            VALUES (#{id}, #{userId}, #{templateId}, #{storedPath}, #{originalFilename},
                    #{mimeType}, #{sizeBytes}, #{width}, #{height}, #{createdAt})
            """)
    int insert(AssetRow row);

    @Select("SELECT " + COLUMNS + " FROM asset WHERE id = #{id}")
    Optional<AssetRow> findById(@Param("id") String id);

    /** 被任务引用过的素材不允许删除（task_asset 外键会拦住，这里先给出可读错误）。 */
    @Select("SELECT COUNT(*) FROM task_asset WHERE asset_id = #{assetId}")
    int countTaskUsages(@Param("assetId") String assetId);

    @Delete("DELETE FROM asset WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") String id, @Param("userId") String userId);
}
