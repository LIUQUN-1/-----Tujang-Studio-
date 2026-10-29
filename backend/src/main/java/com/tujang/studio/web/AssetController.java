package com.tujang.studio.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tujang.studio.service.AssetService;
import com.tujang.studio.web.dto.AssetDto;

/**
 * 素材上传与删除（docs/api.md §3）。
 *
 * <p>上传与提交任务刻意分成两个接口：上传只管"这张图收下了"，提交时才做数量与归属的整体校验。
 * 这样删除/替换单个上传位不必重跑整个提交流程。
 */
@RestController
@RequestMapping("/api/uploads")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "templateId", required = false) String templateId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.upload(file, templateId));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> delete(@PathVariable String assetId) {
        assetService.delete(assetId);
        return ResponseEntity.noContent().build();
    }
}
