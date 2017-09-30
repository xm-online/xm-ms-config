package com.icthh.xm.ms.configuration.config.tenant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Holds information about incoming user.
 */
@RequiredArgsConstructor
@Getter
public class TenantInfo {

    private final String tenant;
    private final String xmToken;
    private final String xmCookie;
    private final String xmUserId;
    private final String xmLocale;
    private final String userLogin;

}
