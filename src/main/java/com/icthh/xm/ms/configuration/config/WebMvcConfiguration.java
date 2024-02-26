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

@Configuration
@Import({XmMsWebConfiguration.class, MsCfgXmRequestContextConfiguration.class})
public class WebMvcConfiguration extends XmWebMvcConfigurerAdapter {

    private final ApplicationProperties properties;
    private final XmRequestContextInterceptor xmRequestContextInterceptor;
    private final AdminApiAccessInterceptor adminApiAccessInterceptor;

    public WebMvcConfiguration(TenantInterceptor tenantInterceptor,
                        XmLoggingInterceptor xmLoggingInterceptor,
                        ApplicationProperties properties,
                        XmRequestContextInterceptor xmRequestContextInterceptor,
                        AdminApiAccessInterceptor adminApiAccessInterceptor) {
        super(tenantInterceptor, xmLoggingInterceptor);
        this.properties = properties;
        this.xmRequestContextInterceptor = xmRequestContextInterceptor;
        this.adminApiAccessInterceptor = adminApiAccessInterceptor;
    }

    @Override
    protected void xmAddInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(xmRequestContextInterceptor).addPathPatterns("/**");
        registry.addInterceptor(adminApiAccessInterceptor).addPathPatterns("/api/**");
    }

    @Override
    protected void xmConfigurePathMatch(PathMatchConfigurer configurer) {
        // no custom configuration
    }

    @Override
    protected List<String> getTenantIgnorePathPatterns() {
        return properties.getTenantIgnoredPathList();
    }
}
