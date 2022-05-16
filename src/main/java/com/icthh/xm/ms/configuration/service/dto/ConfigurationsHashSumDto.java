package com.icthh.xm.ms.configuration.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigurationsHashSumDto {
    private List<ConfigurationHashSum> configurationsHashSum;
}
