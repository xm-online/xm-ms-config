package com.icthh.xm.ms.configuration.config;

import com.icthh.xm.ms.configuration.AbstractUnitTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationPropertiesFileTest extends AbstractUnitTest {

    @Test
    public void fileProperties_haveExpectedDefaults() {
        ApplicationProperties.FileProperties file = new ApplicationProperties.ConfigRepository().getFile();

        assertThat(file).isNotNull();
        assertThat(file.getDebounceTime()).isEqualTo(2);
        assertThat(file.isReadOnly()).isFalse();
        assertThat(file.getPath()).isNull();
    }

    @Test
    public void fileProperties_areSettable() {
        ApplicationProperties.FileProperties file = new ApplicationProperties.FileProperties();
        file.setPath("/etc/xm-config");
        file.setDebounceTime(5);
        file.setReadOnly(true);

        assertThat(file.getPath()).isEqualTo("/etc/xm-config");
        assertThat(file.getDebounceTime()).isEqualTo(5);
        assertThat(file.isReadOnly()).isTrue();
    }
}
