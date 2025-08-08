package com.icthh.xm.ms.configuration.config;

import com.icthh.xm.commons.request.spring.XmRequestContextInterceptor;
import com.icthh.xm.commons.web.spring.TenantInterceptor;
import com.icthh.xm.commons.web.spring.XmLoggingInterceptor;
import com.icthh.xm.commons.web.spring.config.XmMsWebConfiguration;
import com.icthh.xm.commons.web.spring.config.XmWebMvcConfigurerAdapter;
import com.icthh.xm.ms.configuration.security.AdminApiAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;

import java.util.List;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Import({MsCfgXmRequestContextConfiguration.class})
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final XmRequestContextInterceptor xmRequestContextInterceptor;

    public WebMvcConfiguration(XmRequestContextInterceptor xmRequestContextInterceptor) {
        this.xmRequestContextInterceptor = xmRequestContextInterceptor;
    }

    @Override
    public final void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(xmRequestContextInterceptor).addPathPatterns("/**");
    }
}
