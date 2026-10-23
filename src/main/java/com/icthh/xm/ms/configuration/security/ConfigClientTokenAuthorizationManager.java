package com.icthh.xm.ms.configuration.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Allows the private API /api/private/v2 for a service with the token signed by its private key
 * (ConfigPrivateApi of xm-commons). Every node checks the token with application.config-public-key,
 * so the nodes do not share a state.
 */
@Slf4j
public class ConfigClientTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    static final String TOKEN_HEADER = "X-Config-Token";
    static final String AUDIENCE = "config";
    static final long CLOCK_SKEW_SECONDS = 30;

    private final PublicKey publicKey;

    public ConfigClientTokenAuthorizationManager(boolean migration, String publicKey) {
        this.publicKey = readPublicKey(migration, publicKey);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        return new AuthorizationDecision(isValid(context.getRequest().getHeader(TOKEN_HEADER)));
    }

    private boolean isValid(String token) {
        if (publicKey == null || StringUtils.isBlank(token)) {
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .requireAudience(AUDIENCE)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return claims.getExpiration() != null;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Config client token is rejected: {}", e.getMessage());
            return false;
        }
    }

    @SneakyThrows
    private static PublicKey readPublicKey(boolean migration, String publicKey) {
        if (StringUtils.isBlank(publicKey) && migration) {
            log.warn("application.config-public-key is not set: /api/private/v2 rejects every request");
            return null;
        }
        if (StringUtils.isBlank(publicKey)) {
            throw new ConfigPublicKeyNotSetException();
        }
        byte[] encoded = Base64.getDecoder().decode(publicKey.trim());
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
