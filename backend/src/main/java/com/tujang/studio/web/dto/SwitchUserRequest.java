package com.tujang.studio.web.dto;

/**
 * 切换用户（仅 dev）。
 *
 * @param userId 切回某个已存在的用户；省略或不存在时新建一个
 */
public record SwitchUserRequest(String userId) {
}
