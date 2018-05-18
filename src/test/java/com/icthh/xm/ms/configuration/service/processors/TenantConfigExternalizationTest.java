package com.icthh.xm.ms.configuration.service.processors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.ms.configuration.domain.Configuration;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TenantConfigExternalizationTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Test
    public void testConfigOverride() {
        String value = UUID.randomUUID().toString();
        environmentVariables.set("XM_clientCabinetBaseUrl", value);
        Object actual = overrideParameterAndReturnResult(singletonList("clientCabinetBaseUrl"));
        assertEquals(value, actual);
    }

    @Test
    public void testConfigOverrideUpperCase() {
        String value = "1234.1243.2134";
        environmentVariables.set("XM_CLIENTCABINETBASEURL", value);
        Object actual = overrideParameterAndReturnResult(singletonList("clientCabinetBaseUrl"));
        assertEquals(value, actual);
    }

    @Test
    public void testConfigOverrideSubValue() {
        String value = UUID.randomUUID().toString();
        environmentVariables.set("XM_email_subjects_passwordResetEmail_ru", value);
        Object actual = overrideParameterAndReturnResult(asList("email", "subjects", "passwordResetEmail", "ru"));
        assertEquals(value, actual);
    }

    @Test
    public void testConfigOverrideSubValueUpperCase() {
        String value = UUID.randomUUID().toString();
        environmentVariables.set("XM_EMAIL_SUBJECTS_PASSWORDRESETEMAIL_RU", value);
        Object actual = overrideParameterAndReturnResult(asList("email", "subjects", "passwordResetEmail", "ru"));
        assertEquals(value, actual);
    }

    @Test
    public void testConfigOverrideSubValueWithUnderscore() {
        String value = UUID.randomUUID().toString();
        environmentVariables.set("XM_payment_liqpay_public_key", value);
        Object actual = overrideParameterAndReturnResult(asList("payment", "liqpay", "public_key"));
        assertEquals(value, actual);
    }

    @Test
    public void testConfigOverrideSubValueUpperCaseWithUnderscore() {
        String value = UUID.randomUUID().toString();
        environmentVariables.set("XM_PAYMENT_LIQPAY_PUBLIC_KEY", value);
        Object actual = overrideParameterAndReturnResult(asList("payment", "liqpay", "public_key"));
        assertEquals(value, actual);
    }

    @Test
    public void overrideIntegerNumbers() {
        String value = "" + 123;
        environmentVariables.set("XM_inviteExpireTime", value);
        Object actual = overrideParameterAndReturnResult(asList("inviteExpireTime"));
        assertEquals(123, actual);
    }

    @Test
    public void overrideLongNumbers() {
        String value = "" + 1236484563242346716L;
        environmentVariables.set("XM_inviteExpireTime", value);
        Object actual = overrideParameterAndReturnResult(asList("inviteExpireTime"));
        assertEquals(1236484563242346716L, actual);
    }

    @Test
    public void overrideDoubleNumbers() {
        String value = "" + 123.5;
        environmentVariables.set("XM_inviteExpireTime", value);
        Object actual = overrideParameterAndReturnResult(asList("inviteExpireTime"));
        assertEquals(123.5, actual);
    }

    @Test
    public void noyOverideByOtherTenant() {
        String value = "" + 123.5;
        environmentVariables.set("OTHER_inviteExpireTime", value);
        Object actual = overrideParameterAndReturnResult(asList("inviteExpireTime"));
        assertEquals(86400, actual);
    }


    @SneakyThrows
    private Object overrideParameterAndReturnResult(List<String> path) {
        Configuration configuration = new Configuration("/config/tenants/XM/tenant-config.yml", loadFile("tenant-config.yml"));
        Configuration processedConfiguration = new TenantConfigExternalization().processConfiguration(configuration);
        Map<String, Object> configMap = mapper.readValue(processedConfiguration.getContent(), Map.class);
        Object map = configMap;
        for(String key: path) {
            map = ((Map)map).get(key);
        }
        return map;
    }

    @SneakyThrows
    public static String loadFile(String path) {
        InputStream cfgInputStream = new ClassPathResource(path).getInputStream();
        return IOUtils.toString(cfgInputStream, UTF_8);
    }

}