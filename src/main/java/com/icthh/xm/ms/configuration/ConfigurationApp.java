package com.icthh.xm.ms.configuration;

import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import tech.jhipster.config.DefaultProfileUtil;
import tech.jhipster.config.JHipsterConstants;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.icthh.xm")
@EnableConfigurationProperties({ApplicationProperties.class})
public class ConfigurationApp {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationApp.class);

    private final Environment env;
    private final TenantContextHolder tenantContextHolder;

    public ConfigurationApp(Environment env, TenantContextHolder tenantContextHolder) {
        this.env = env;
        this.tenantContextHolder = tenantContextHolder;
    }

    /**
     * Initializes configuration.
     * <p>
     * Spring profiles can be configured with a program argument --spring.profiles.active=your-active-profile
     * <p>
     * You can find more information on how profiles work with JHipster on <a href="https://www.jhipster.tech/profiles/">https://www.jhipster.tech/profiles/</a>.
     */
    @PostConstruct
    public void initApplication() {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            log.error("You have misconfigured your application! It should not run " +
                "with both the 'dev' and 'prod' profiles at the same time.");
        }
        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_CLOUD)) {
            log.error("You have misconfigured your application! It should not " +
                "run with both the 'dev' and 'cloud' profiles at the same time.");
        }

        initContexts();
    }

    private void initContexts() {
        // init tenant context, by default this is XM super tenant
        TenantContextUtils.setTenant(tenantContextHolder, TenantKey.SUPER);

        // init logger MDC context
        MdcUtils.putRid(MdcUtils.generateRid() + "::" + TenantKey.SUPER.getValue());
    }

    /**
     * Destroy configuration.
     */
    @PreDestroy
    public void destroyApplication() {
        log.info(
            "\n----------------------------------------------------------\n\t" +
                "Application {} is closing" +
                "\n----------------------------------------------------------",
            env.getProperty("spring.application.name")
        );
    }

    /**
     * Main method, used to run the application.
     *
     * @param args the command line arguments
     */
    @SneakyThrows
    public static void main(String[] args) {
        MdcUtils.putRid();
        SpringApplication app = new SpringApplication(ConfigurationApp.class);
        DefaultProfileUtil.addDefaultProfile(app);
        ConfigurableApplicationContext context = app.run(args);
        Environment env = context.getEnvironment();
        logApplicationStartup(env);
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = Optional.ofNullable(env.getProperty("server.ssl.key-store")).map(key -> "https").orElse("http");
        String applicationName = env.getProperty("spring.application.name");
        String serverPort = env.getProperty("server.port");
        String contextPath = Optional
            .ofNullable(env.getProperty("server.servlet.context-path"))
            .filter(StringUtils::isNotBlank)
            .orElse("/");
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("The host name could not be determined, using `localhost` as fallback");
        }
        log.info("\n----------------------------------------------------------\n\t"
                + "Application '{}' is running! Access URLs:\n\t"
                + "Local: \t\t{}://localhost:{}{}\n\t"
                + "External: \t{}://{}:{}{}\n\t"
                + "Timezone: \t{}\n\t"
                + "Profile(s): \t{}\n----------------------------------------------------------",
            env.getProperty("spring.application.name"),
            protocol,
            serverPort,
            contextPath,
            protocol,
            hostAddress,
            serverPort,
            contextPath,
            TimeZone.getDefault().toZoneId().getId(),
            env.getActiveProfiles());
    }
}
