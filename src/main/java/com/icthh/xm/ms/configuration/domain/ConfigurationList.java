package com.icthh.xm.ms.configuration.domain;

import com.icthh.xm.commons.config.domain.Configuration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class ConfigurationList {

    private final String commit;
    private final List<Configuration> data;
}
