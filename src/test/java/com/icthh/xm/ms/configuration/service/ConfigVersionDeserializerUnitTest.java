package com.icthh.xm.ms.configuration.service;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigVersionDeserializerUnitTest {

    @InjectMocks
    private ConfigVersionDeserializer deserializer;

    @Test
    public void testDeserializeWhenVersionIsNull() {
        ConfigVersion result = deserializer.from("");
        assertEquals(ConfigVersion.UNDEFINED_VERSION, result);
    }

    @Test
    public void testFromWithNullString() {
        ConfigVersion result = deserializer.from(null);
        assertEquals(ConfigVersion.UNDEFINED_VERSION, result);
    }

    @Test
    public void testFromWithValidJson() {
        String validJson = "{\"mainVersion\":\"1.0.0\"}";
        ConfigVersion result = deserializer.from(validJson);
        assertEquals(result.getMainVersion(), "1.0.0");
    }

    @Test
    public void testFromWithInvalidJson() {
        String invalidJson = "invalid json";
        ConfigVersion result = deserializer.from(invalidJson);
        assertNotNull(result);
        assertEquals(invalidJson, result.getMainVersion());
    }
}
