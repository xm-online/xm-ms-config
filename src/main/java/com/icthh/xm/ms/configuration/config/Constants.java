package com.icthh.xm.ms.configuration.config;

import lombok.experimental.UtilityClass;

/**
 * Application constants.
 */
@UtilityClass
public final class Constants {

    public static final String TENANT_NAME = "tenantName";
    public static final String TENANT_PREFIX = "/config/tenants/";
    public static final String TENANT_ENV_PATTERN = TENANT_PREFIX + "{" + TENANT_NAME + "}/**";

    public static final String AUTH_TENANT_KEY = "tenant";
    public static final String AUTH_XM_TOKEN_KEY = "xmToken";
    public static final String AUTH_XM_COOKIE_KEY = "xmCookie";
    public static final String AUTH_XM_USERID_KEY = "xmUserID";
    public static final String AUTH_XM_LOCALE_KEY = "xmLocale";
    public static final String HEADER_TENANT = "x-tenant";

    public static final String SYSTEM_ACCOUNT = "system";

    public static final String CERTIFICATE = "X.509";
    public static final String PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----%n%s%n-----END PUBLIC KEY-----";

    public static final String PRIVATE = "/private";
    public static final String API_PREFIX = "/api";
    public static final String TENANTS = "/tenants";
    public static final String CONFIG = "/config";
    public static final String INMEMORY = "/inmemory";
    public static final String PROFILE = "/profile";
    public static final String PUBLIC_KEY_FILE = "/public.cer";
    public static final String REFRESH = "/refresh";
    public static final String RECLONE = "/reclone";

}
