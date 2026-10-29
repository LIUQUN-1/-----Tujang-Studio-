package com.tujang.studio.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tujang.studio.domain.CategoryCount;
import com.tujang.studio.domain.TemplateRow;

/**
 * 模板读取。P1 只读——题面不要求模板 CRUD。
 *
 * <p>不用动态 SQL 拼 category 条件，而是给"全部"和"按分类"两个方法：
 * 少一层 {@code <script>} 与 null 判断，SQL 也更容易在日志里看清。
 */
@Mapper
public interface TemplateMapper {

    String COLUMNS = """
            t.id, t.title, t.category_code, c.name AS category_name,
            t.cover_url, t.description, t.required_image_count,
            t.accepted_mime_types, t.max_file_size_bytes
            """;

    @Select("SELECT " + COLUMNS + """
            FROM template t
            JOIN category c ON c.code = t.category_code
            ORDER BY t.sort_order, t.id
            """)
    List<TemplateRow> findAll();

    @Select("SELECT " + COLUMNS + """
            FROM template t
            JOIN category c ON c.code = t.category_code
            WHERE t.category_code = #{category}
            ORDER BY t.sort_order, t.id
            """)
    List<TemplateRow> findByCategory(@Param("category") String category);

    @Select("SELECT " + COLUMNS + """
            FROM template t
            JOIN category c ON c.code = t.category_code
            WHERE t.id = #{id}
            """)
    Optional<TemplateRow> findById(@Param("id") String id);

    /** 只返回有模板的分类；没有模板的分类不会出现在 Tab 里。 */
    @Select("""
            SELECT c.code, c.name, COUNT(t.id) AS count
            FROM category c
            LEFT JOIN template t ON t.category_code = c.code
            GROUP BY c.code, c.name, c.sort_order
            HAVING COUNT(t.id) > 0
            ORDER BY c.sort_order
            """)
    List<CategoryCount> findCategoryCounts();
}
