package com.tujang.studio.domain;

/** 任务用到的素材（task_asset join asset），按 position 排序。 */
public record TaskAssetRef(String assetId, String storedPath, int position) {

    /** {@code assets/xxx.png} → {@code /files/assets/xxx.png} */
    public String url() {
        String name = storedPath.substring(storedPath.lastIndexOf('/') + 1);
        return "/files/assets/" + name;
    }
}
