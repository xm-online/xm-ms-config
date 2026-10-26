package com.icthh.xm.ms.configuration.security;

import static com.icthh.xm.commons.config.client.repository.ConfigPrivateApi.AUDIENCE;
import static com.icthh.xm.commons.config.client.repository.ConfigPrivateApi.TOKEN_HEADER;
import static com.icthh.xm.commons.config.client.repository.ConfigPrivateApi.TOKEN_TTL;

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
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Supplier;

@Slf4j
public class ConfigClientTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final long CLOCK_SKEW_SECONDS = 30;

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
            return isExpirationAllowed(claims.getExpiration());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Config client token is rejected: {}", e.getMessage());
            return false;
        }
    }

    private static boolean isExpirationAllowed(Date expiration) {
        return expiration != null
            && !expiration.toInstant().isAfter(Instant.now().plus(TOKEN_TTL).plusSeconds(CLOCK_SKEW_SECONDS));
    }

    @SneakyThrows
    private static PublicKey readPublicKey(boolean migration, String publicKey) {
        if (StringUtils.isBlank(publicKey)) {
            if (!migration) {
                throw new ConfigPublicKeyNotSetException();
            }
            log.warn("application.config-public-key is not set: /api/private/v2 rejects every request");
            return null;
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(publicKey.trim());
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (IllegalArgumentException | InvalidKeySpecException e) {
            throw new IllegalStateException("application.config-public-key is not a base64 X.509 DER "
                + "Ed25519 public key: " + e.getMessage(), e);
        }
    }
}
