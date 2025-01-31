package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileService {

    private final ApplicationProperties applicationProperties;

    @SneakyThrows
    public String readFileToString(String filePath) {
        byte[] encoded = Files.readAllBytes(Paths.get(filePath));
        return writeAsString(filePath, encoded);
    }

    public String writeAsString(String filePath,
                                       byte[] content) {
        return applicationProperties.getBinaryFileTypes()
            .stream()
            .filter(filePath::endsWith)
            .findAny()
            .map(s -> Base64.getEncoder().encodeToString(content))
            .orElse(new String(content, StandardCharsets.UTF_8));
    }

    @SneakyThrows
    public static long countOfFilesInDirectoryRecursively(String directoryPath) {
        Path start = Paths.get(directoryPath);
        if (!start.toFile().exists()) {
            log.warn("Directory {} does not exist", directoryPath);
            return 0;
        }
        return Files.walk(start).filter(Files::isRegularFile).count();
    }

    @SneakyThrows
    public static List<String> listOfFilesInDirectoryRecursively(String directoryPath) {
        return Files.walk(Paths.get(directoryPath))
            .map(Path::toString)
            .collect(Collectors.toList());
    }

}
