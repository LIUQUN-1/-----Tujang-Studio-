package com.tujang.studio.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tujang.studio.service.TaskService;
import com.tujang.studio.web.dto.SubmitTaskRequest;
import com.tujang.studio.web.dto.TaskDto;

/**
 * 制作任务接口（docs/api.md §4）。
 *
 * <p>提交返回 <b>202</b>：语义是"已受理、处理中"，不是"已完成"。
 * 查询接口只读——它不驱动任务前进，任务只由后台执行器推进。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskDto> submit(@RequestBody SubmitTaskRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(taskService.submit(request));
    }

    @GetMapping("/{id}")
    public TaskDto get(@PathVariable String id) {
        return taskService.get(id);
    }

    @GetMapping
    public List<TaskDto> history(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return taskService.history(limit);
    }
}
