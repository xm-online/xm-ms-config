package com.icthh.xm.ms.configuration.web.rest.dto;

import com.icthh.xm.ms.configuration.utils.ConfigPathUtils;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConfigPatternPathDTO {
    private List<String> paths;
    private String version;

    @Override
    public String toString() {
        return "ConfigPatternPathDTO{" +
                "version = " + version +
                ", paths.length = " + (paths != null ? paths.size() : null) +
                ", top paths = " + ConfigPathUtils.printPathsWithLimit(paths) +
                "}";
    }
}
