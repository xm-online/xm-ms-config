package com.icthh.xm.ms.configuration.utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtils {

    @SneakyThrows
    public static String readFileToString(String filePath) {
        byte[] encoded = Files.readAllBytes(Paths.get(filePath));
        return new String(encoded, StandardCharsets.UTF_8);
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
