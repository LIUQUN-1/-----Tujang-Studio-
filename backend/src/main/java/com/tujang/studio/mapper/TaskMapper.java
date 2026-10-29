package com.tujang.studio.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tujang.studio.domain.TaskAssetRef;
import com.tujang.studio.domain.TaskRow;

/**
 * 任务读写。
 *
 * <p>所有状态迁移都是**条件 UPDATE**：WHERE 里带上"当前状态必须是什么"，
 * 用受影响行数判断是否抢到了这次迁移。好处是"终态不可回退"和"重复执行不重复推进"
 * 由数据库保证，而不是靠 Java 里的 if——并发与重复调用都安全。
 */
@Mapper
public interface TaskMapper {

    String COLUMNS = """
            t.id, t.user_id, t.template_id,
            tp.title AS template_title, tp.cover_url AS template_cover_url,
            t.status, t.current_step, t.failed_step, t.error_code, t.error_message,
            t.result_url, t.result_width, t.result_height,
            t.sim_fail_step, t.sim_durations,
            t.created_at, t.started_at, t.finished_at, t.updated_at
            """;

    String FROM = " FROM task t JOIN template tp ON tp.id = t.template_id ";

    @Insert("""
            INSERT INTO task (id, user_id, template_id, status,
                              sim_fail_step, sim_durations, created_at, updated_at)
            VALUES (#{id}, #{userId}, #{templateId}, 'PENDING',
                    #{simFailStep}, #{simDurations}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """)
    int insert(TaskRow row);

    @Insert("""
            INSERT INTO task_asset (task_id, asset_id, position)
            VALUES (#{taskId}, #{assetId}, #{position})
            """)
    int insertTaskAsset(
            @Param("taskId") String taskId,
            @Param("assetId") String assetId,
            @Param("position") int position);

    @Select("SELECT " + COLUMNS + FROM + "WHERE t.id = #{id}")
    Optional<TaskRow> findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + FROM + "WHERE t.user_id = #{userId} ORDER BY t.created_at DESC, t.id DESC LIMIT #{limit}")
    List<TaskRow> findByUser(@Param("userId") String userId, @Param("limit") int limit);

    @Select("""
            SELECT ta.asset_id, a.stored_path, ta.position
            FROM task_asset ta
            JOIN asset a ON a.id = ta.asset_id
            WHERE ta.task_id = #{taskId}
            ORDER BY ta.position
            """)
    List<TaskAssetRef> findInputs(@Param("taskId") String taskId);

    /** PENDING → PROCESSING(第一步)。抢不到（已被处理或已终态）返回 0。 */
    @Update("""
            UPDATE task
            SET status = 'PROCESSING', current_step = #{step},
                started_at = COALESCE(started_at, UTC_TIMESTAMP(3)),
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int beginProcessing(@Param("id") String id, @Param("step") String step);

    /** 推进到下一步；仅当仍处于 PROCESSING 时生效。 */
    @Update("""
            UPDATE task
            SET current_step = #{step}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int advanceStep(@Param("id") String id, @Param("step") String step);

    @Update("""
            UPDATE task
            SET status = 'SUCCEEDED', current_step = 'FINALIZE',
                result_url = #{resultUrl}, result_width = #{width}, result_height = #{height},
                finished_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markSucceeded(
            @Param("id") String id,
            @Param("resultUrl") String resultUrl,
            @Param("width") Integer width,
            @Param("height") Integer height);

    /** 失败可以从 PENDING 或 PROCESSING 进入；已经是终态则不覆盖。 */
    @Update("""
            UPDATE task
            SET status = 'FAILED', failed_step = #{step}, error_code = #{code}, error_message = #{message},
                finished_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status NOT IN ('SUCCEEDED', 'FAILED')
            """)
    int markFailed(
            @Param("id") String id,
            @Param("step") String step,
            @Param("code") String code,
            @Param("message") String message);
}
