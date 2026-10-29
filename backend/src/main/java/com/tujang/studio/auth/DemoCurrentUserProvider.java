package com.tujang.studio.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * P2/P3 期间的临时实现：所有请求都算作种子里的演示用户。
 *
 * <p>之所以现在就抽出接口，是因为素材和任务都要挂 {@code user_id}；
 * 先有归属，P6 接上真实身份时只换实现、不动业务代码。
 */
@Component
public class DemoCurrentUserProvider implements CurrentUserProvider {

    private final String demoUserId;

    public DemoCurrentUserProvider(@Value("${app.demo-user-id}") String demoUserId) {
        this.demoUserId = demoUserId;
    }

    @Override
    public String currentUserId() {
        return demoUserId;
    }
}
