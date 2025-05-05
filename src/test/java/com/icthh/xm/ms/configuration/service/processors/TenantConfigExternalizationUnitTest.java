package com.icthh.xm.ms.configuration.service.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractUnitTest;
import com.icthh.xm.ms.configuration.web.rest.TestUtil;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class TenantConfigExternalizationUnitTest extends AbstractUnitTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Before
    public void before() {
        Locale.setDefault(Locale.US);
    }

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
        environmentVariables.set("XM_email_subjects_passwordChangedEmail_ru", value);
        Object actual = overrideParameterAndReturnResult(asList("email", "subjects", "passwordChangedEmail", "ru"));
        assertEquals(value, actual);
    }

    @Test
    public void testConfigOverrideSubValueUpperCase() {
        String value = UUID.randomUUID().toString();
        environmentVariables.set("XM_EMAIL_SUBJECTS_PASSWORDCHANGEDEMAIL_RU", value);
        Object actual = overrideParameterAndReturnResult(asList("email", "subjects", "passwordChangedEmail", "ru"));
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
        Object actual = overrideParameterAndReturnResult(singletonList("inviteExpireTime"));
        assertEquals(123.5, actual);
    }

    @Test
    public void noyOverrideByOtherTenant() {
        String value = "" + 123.5;
        environmentVariables.set("OTHER_inviteExpireTime", value);
        Object actual = overrideParameterAndReturnResult(singletonList("inviteExpireTime"));
        assertEquals(86400, actual);
    }


    @SneakyThrows
    private Object overrideParameterAndReturnResult(List<String> path) {
        Configuration configuration = new Configuration("/config/tenants/XM/tenant-config.yml", TestUtil.loadFile("tenant-config.yml"));
        List<Configuration> processedConfigurations = new TenantConfigExternalization()
            .processConfiguration(configuration, emptyMap(), emptyMap(), new HashSet<>(), new HashMap<>());
        Configuration processedConfiguration = configuration;
        if (!processedConfigurations.isEmpty()) {
            processedConfiguration = processedConfigurations.get(0);
        }
        Map<String, Object> configMap = mapper.readValue(processedConfiguration.getContent(), Map.class);
        Object map = configMap;
        for (String key : path) {
            map = ((Map) map).get(key);
        }
        return map;
    }

}
