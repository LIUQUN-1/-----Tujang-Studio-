package com.tujang.studio.auth;

/**
 * 当前用户解析。P2 先固定用种子里的演示用户，P6 换成 Cookie 解析实现
 * （{@code tujang_uid} → app_user），调用方不需要改。
 */
public interface CurrentUserProvider {

    String currentUserId();
}
