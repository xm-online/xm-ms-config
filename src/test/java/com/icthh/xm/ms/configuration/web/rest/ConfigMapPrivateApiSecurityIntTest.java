package com.icthh.xm.ms.configuration.web.rest;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test keys only: the service signs with PRIVATE_KEY, xm-ms-config checks with the public key of the same pair
 * from the test application.yml.
 */
public class ConfigMapPrivateApiSecurityIntTest extends AbstractSpringBootTest {

    static final String TOKEN_HEADER = "X-Config-Token";
    private static final String PRIVATE_KEY = "MC4CAQAwBQYDK2VwBCIEIOIvuCuM2px95QXJIqbjhd2ZvQjXq5C83PtrriD4d7ar";
    private static final String OTHER_PRIVATE_KEY = "MC4CAQAwBQYDK2VwBCIEIPoQHdQ9DJDQocfwvUsh2lv3SX7sdVA+SqDFbz2cLs9p";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ConfigurationService configurationService;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(configurationService.getConfigurationMap(any()))
            .thenReturn(Map.of("path", new Configuration("path", "content")));
    }

    @Test
    @SneakyThrows
    public void testNewApiReturnsConfigWithValidToken() {
        mockMvc.perform(get("/api/private/v2/config_map").header(TOKEN_HEADER, token(PRIVATE_KEY).compact()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path.content").value("content"));
    }

    @Test
    @SneakyThrows
    public void testNewApiPatternAcceptsValidToken() {
        mockMvc.perform(post("/api/private/v2/config_map/pattern")
                .header(TOKEN_HEADER, token(PRIVATE_KEY).compact())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":null,\"patternPaths\":[\"/config/tenants/**\"]}"))
            .andExpect(status().isOk());
    }

    @Test
    @SneakyThrows
    public void testNewApiRejectsMissingOrWrongToken() {
        assertRejected(null);
        assertRejected("not-a-jwt");
        assertRejected(token(OTHER_PRIVATE_KEY).compact());
        assertRejected(token(PRIVATE_KEY, "uaa").compact());
        assertRejected(token(PRIVATE_KEY).expiration(Date.from(Instant.now().minusSeconds(120))).compact());
        assertRejected(token(PRIVATE_KEY).expiration(null).compact());
    }

    @Test
    @SneakyThrows
    public void testOldApiIsClosedWithoutMigration() {
        mockMvc.perform(get("/api/private/config_map")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/private/config_map").header(TOKEN_HEADER, token(PRIVATE_KEY).compact()))
            .andExpect(status().is4xxClientError());
    }

    @SneakyThrows
    private void assertRejected(String token) {
        var request = get("/api/private/v2/config_map");
        if (token != null) {
            request.header(TOKEN_HEADER, token);
        }
        mockMvc.perform(request).andExpect(status().is4xxClientError());
    }

    static JwtBuilder token(String privateKey) {
        return token(privateKey, "config");
    }

    static JwtBuilder token(String privateKey, String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer("entity")
            .audience().add(audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(300)))
            .signWith(readPrivateKey(privateKey), Jwts.SIG.EdDSA);
    }

    @SneakyThrows
    private static PrivateKey readPrivateKey(String privateKey) {
        return KeyFactory.getInstance("Ed25519")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
    }
}
