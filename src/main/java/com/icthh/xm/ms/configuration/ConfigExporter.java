package com.icthh.xm.ms.configuration;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.export.ExportBeanConfiguration;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
        if (args.length != 2) {
            log.error("Usage: java ConfigExporter <path-to-config-repository> <path-to-output.zip>");
            System.exit(1);
            return;
        }

        String repoPath = args[0];
        String outputPath = resolveOutputPath(args[1]);

        log.info("Exporting configuration from [{}] to [{}]", repoPath, outputPath);

        SpringApplication app = new SpringApplication(ExportBeanConfiguration.class);
        app.setAdditionalProfiles(EXPORT_PROFILE);
        app.setListeners(List.of(new EnvironmentPostProcessorApplicationListener()));
        app.setDefaultProperties(Map.of(
            "application.git.uri", repoPath
        ));

        try (ConfigurableApplicationContext ctx = app.run()) {
            ConfigurationService configurationService = ctx.getBean(ConfigurationService.class);

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

    private static String resolveOutputPath(String path) {
        File file = new File(path);
        if (file.isDirectory()) {
            return new File(file, CONFIG_EXPORT_ZIP).getAbsolutePath();
        }
        return path;
    }

    private static void writeZip(Map<String, Configuration> configs, String outputPath) throws IOException {
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

    private static String normalizePath(String path) {
        return path != null && path.startsWith("/") ? path.substring(1) : path;
    }
}
