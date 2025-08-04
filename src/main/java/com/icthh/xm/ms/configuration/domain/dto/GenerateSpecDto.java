package com.icthh.xm.ms.configuration.domain.dto;

import java.util.List;
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
    public static class SpecJsonPathDto {
        private String className;
        private String jsonPath;
    }
}
