package com.icthh.xm.ms.configuration.service.generator.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenerateSpecDto {

    private String microserviceName;
    private String specKey;
    private List<SpecJsonPathDto> jsonPaths;
    private List<String> specPathsAntPattern;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecJsonPathDto {
        private String className;
        private String jsonPath;
    }
}
