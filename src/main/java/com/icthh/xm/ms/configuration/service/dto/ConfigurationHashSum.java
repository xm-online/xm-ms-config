package com.icthh.xm.ms.configuration.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigurationHashSum {
    Map<String, String> pathToHashSum;
}
