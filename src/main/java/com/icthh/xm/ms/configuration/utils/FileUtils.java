package com.icthh.xm.ms.configuration.utils;

import lombok.SneakyThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {

    @SneakyThrows
    public static String readFileToString(String filePath) {
        byte[] encoded = Files.readAllBytes(Paths.get(filePath));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    @SneakyThrows
    public static long countOfFilesInDirectoryRecursively(String directoryPath) {
        return Files.walk(Paths.get(directoryPath)).filter(Files::isRegularFile).count();
    }

}
