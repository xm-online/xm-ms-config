package com.icthh.xm.ms.configuration.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigurationHashSum {
    String path;
    String hashSum;
}
