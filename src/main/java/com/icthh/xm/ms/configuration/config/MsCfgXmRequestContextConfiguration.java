package com.icthh.xm.ms.configuration.config;

import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_TYPE;

import com.icthh.xm.commons.request.spring.config.InterceptorXmRequestContextConfiguration;
import com.icthh.xm.ms.configuration.domain.RequestSourceType;
import org.springframework.context.annotation.Configuration;

/**
 * The {@link MsCfgXmRequestContextConfiguration} class.
 */
@Configuration
public class MsCfgXmRequestContextConfiguration extends InterceptorXmRequestContextConfiguration {

    public MsCfgXmRequestContextConfiguration() {
        super(REQUEST_SOURCE_TYPE, RequestSourceType.WEB_SERVICE);
    }

}
