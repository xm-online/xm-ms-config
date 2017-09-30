package com.icthh.xm.ms.configuration.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "path")
public class Configuration {

    private String path;

    private String content;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Configuration{");
        sb.append("path='").append(path).append('\'');
        if (content != null && content.length() > 100) {
            sb.append(", content.length='").append(content.length()).append('\'');
        } else {
            sb.append(", content='").append(content).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
