package com.tujang.studio.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tujang.studio.auth.CurrentUserProvider;
import com.tujang.studio.domain.AssetRow;
import com.tujang.studio.mapper.AssetMapper;
import com.tujang.studio.support.ImageInspector;
import com.tujang.studio.support.Sizes;
import com.tujang.studio.support.StorageService;
import com.tujang.studio.support.Times;
import com.tujang.studio.support.Ulid;
import com.tujang.studio.web.ApiException;
import com.tujang.studio.web.dto.AssetDto;
import com.tujang.studio.web.dto.TemplateDetailDto;

/**
 * 素材上传与删除。
 *
 * <p>校验顺序刻意固定为 **大小 → 类型 → 能否解码 → 归属/引用**，
 * 因为越贵的检查越靠后：大小只看元数据，解码要把整个文件读进来。
 * 客户端声明的 Content-Type 一律不采信，只认文件内容。
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private final AssetMapper assetMapper;
    private final TemplateService templateService;
    private final StorageService storageService;
    private final CurrentUserProvider currentUser;

    private final List<String> defaultAllowedMimes;
    private final long defaultMaxSizeBytes;

    public AssetService(
            AssetMapper assetMapper,
            TemplateService templateService,
            StorageService storageService,
            CurrentUserProvider currentUser,
            @Value("${app.upload.default-allowed-mimes}") String defaultAllowedMimes,
            @Value("${app.upload.default-max-size-bytes}") long defaultMaxSizeBytes) {
        this.assetMapper = assetMapper;
        this.templateService = templateService;
        this.storageService = storageService;
        this.currentUser = currentUser;
        this.defaultAllowedMimes = splitCsv(defaultAllowedMimes);
        this.defaultMaxSizeBytes = defaultMaxSizeBytes;
    }

    @Transactional
    public AssetDto upload(MultipartFile file, String templateId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.invalid("请选择要上传的图片", java.util.Map.of("file", "文件为空"));
        }

        // 带了模板就按模板的约束校验；没带则用全局默认（宽松）
        List<String> allowed = defaultAllowedMimes;
        long maxBytes = defaultMaxSizeBytes;
        String effectiveTemplateId = null;
        if (templateId != null && !templateId.isBlank()) {
            TemplateDetailDto template = templateService.detail(templateId.trim());
            if (!template.acceptedMimeTypes().isEmpty()) {
                allowed = template.acceptedMimeTypes();
            }
            if (template.maxFileSizeBytes() > 0) {
                maxBytes = template.maxFileSizeBytes();
            }
            effectiveTemplateId = template.id();
        }

        long size = file.getSize();
        if (size > maxBytes) {
            throw ApiException.payloadTooLarge("FILE_TOO_LARGE",
                    "文件 %s，超过上限 %s".formatted(Sizes.human(size), Sizes.human(maxBytes)));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传文件失败", ex);
        }

        var inspected = ImageInspector.inspect(bytes);
        if (inspected.isEmpty()) {
            // 魔数都不认识 → 类型不支持；魔数认识但解不开 → 文件损坏（改扩展名的假图落在这里）
            if (ImageInspector.hasKnownImageSignature(bytes)) {
                throw ApiException.badRequest("FILE_NOT_DECODABLE",
                        "图片无法解析，文件可能已损坏或只是修改了扩展名");
            }
            throw ApiException.unsupportedMediaType("UNSUPPORTED_MEDIA_TYPE",
                    "不支持的文件类型，仅接受 " + String.join("、", allowed));
        }

        var info = inspected.get();
        if (!allowed.contains(info.mimeType())) {
            throw ApiException.unsupportedMediaType("UNSUPPORTED_MEDIA_TYPE",
                    "该模板仅接受 " + String.join("、", allowed) + "，当前为 " + info.mimeType());
        }

        String userId = currentUser.currentUserId();
        String assetId = Ulid.next();
        String storedPath = storageService.storeAsset(assetId, info.extension(), bytes);

        AssetRow row = new AssetRow(
                assetId,
                userId,
                effectiveTemplateId,
                storedPath,
                sanitizeFilename(file.getOriginalFilename()),
                info.mimeType(),
                size,
                info.width(),
                info.height(),
                Times.nowUtc());

        try {
            assetMapper.insert(row);
        } catch (RuntimeException ex) {
            // 写库失败就把刚落的文件删掉，避免留下孤儿文件
            storageService.deleteAsset(storedPath);
            throw ex;
        }

        log.debug("asset uploaded: id={} owner={} mime={} size={} {}x{}",
                assetId, userId, info.mimeType(), size, info.width(), info.height());

        return new AssetDto(
                assetId,
                "/files/assets/" + storedPath.substring("assets/".length()),
                row.originalFilename(),
                row.mimeType(),
                row.sizeBytes(),
                row.width(),
                row.height(),
                row.createdAt().toInstant(java.time.ZoneOffset.UTC));
    }

    @Transactional
    public void delete(String assetId) {
        String userId = currentUser.currentUserId();
        AssetRow row = assetMapper.findById(assetId)
                .orElseThrow(() -> ApiException.notFound("ASSET_NOT_FOUND", "素材不存在或已被清理"));

        if (!row.userId().equals(userId)) {
            throw ApiException.forbidden("ASSET_FORBIDDEN", "无权操作该素材");
        }
        if (assetMapper.countTaskUsages(assetId) > 0) {
            throw ApiException.conflict("ASSET_IN_USE", "该素材已被制作任务使用，不能删除");
        }

        assetMapper.deleteByIdAndUser(assetId, userId);
        storageService.deleteAsset(row.storedPath());
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[\\p{Cntrl}]", "").trim();
        if (base.isEmpty()) {
            return "unnamed";
        }
        return base.length() <= 255 ? base : base.substring(base.length() - 255);
    }

    private static List<String> splitCsv(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
