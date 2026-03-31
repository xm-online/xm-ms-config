package com.icthh.xm.ms.configuration;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.export.ExportBeanConfiguration;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessorApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
public class ConfigExporter {

    private static final String CONFIG_EXPORT_ZIP = "config-export.zip";
    private static final String EXPORT_PROFILE = "export";

    public static void main(String[] args) {
        new ConfigExporter().execute(args);
    }

    protected void execute(String[] args) {
        if (args.length != 2) {
            log.error("Usage: java ConfigExporter <path-to-config-repository> <path-to-output.zip>");
            System.exit(1);
            return;
        }

        String repoPath = args[0];
        String outputPath = resolveOutputPath(args[1]);

        log.info("Exporting configuration from [{}] to [{}]", repoPath, outputPath);

        try (ConfigurableApplicationContext ctx = createApplicationContext(repoPath)) {
            ConfigurationService configurationService = ctx.getBean(ConfigurationService.class);
            TenantAliasTreeService tenantAliasTreeService = ctx.getBean(TenantAliasTreeService.class);

            log.info("Updating tenant alias tree...");
            Optional<Configuration> configuration = configurationService.findConfiguration(TenantAliasTreeService.TENANT_ALIAS_CONFIG);
            configuration.ifPresent(value ->
                tenantAliasTreeService.updateAliasTree(new Configuration(TenantAliasTreeService.TENANT_ALIAS_CONFIG, value.getContent())));
            log.info("Tenant alias tree updated");

            log.info("Reading configuration map...");
            Map<String, Configuration> configs = configurationService.getConfigurationMap(null);
            log.info("Loaded {} configuration entries", configs.size());

            writeZip(configs, outputPath);
            log.info("Export completed successfully: {}", outputPath);
        } catch (IOException e) {
            log.error("Failed to write zip file: {}", outputPath, e);
            System.exit(1);
        } catch (Exception e) {
            log.error("Export failed", e);
            System.exit(1);
        }
    }

    protected ConfigurableApplicationContext createApplicationContext(String repoPath) {
        SpringApplication app = new SpringApplication(ExportBeanConfiguration.class);
        app.setAdditionalProfiles(EXPORT_PROFILE);
        app.setListeners(List.of(new EnvironmentPostProcessorApplicationListener()));
        app.setDefaultProperties(Map.of(
            "application.git.uri", repoPath
        ));
        return app.run();
    }

    private String resolveOutputPath(String path) {
        File file = new File(path);
        if (file.isDirectory()) {
            return new File(file, CONFIG_EXPORT_ZIP).getAbsolutePath();
        }
        return path;
    }

    private void writeZip(Map<String, Configuration> configs, String outputPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputPath))) {
            for (Map.Entry<String, Configuration> entry : configs.entrySet()) {
                Configuration config = entry.getValue();
                if (config == null || config.getContent() == null) {
                    continue;
                }

                String path = normalizePath(config.getPath());
                zos.putNextEntry(new ZipEntry(path));
                zos.write(config.getContent().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    private String normalizePath(String path) {
        return path != null && path.startsWith("/") ? path.substring(1) : path;
    }
}
