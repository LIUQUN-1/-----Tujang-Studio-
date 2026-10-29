package com.tujang.studio.web.dto;

import java.util.List;

/** 提交制作任务的请求体（docs/api.md §4.1）。 */
public record SubmitTaskRequest(String templateId, List<String> assetIds) {
}
