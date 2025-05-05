package com.icthh.xm.ms.configuration.service;


import com.icthh.xm.ms.configuration.AbstractUnitTest;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import org.junit.Test;
import org.mockito.InjectMocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigVersionDeserializerUnitTest extends AbstractUnitTest {

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
