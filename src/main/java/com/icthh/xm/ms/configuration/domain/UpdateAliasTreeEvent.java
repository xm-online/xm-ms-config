package com.icthh.xm.ms.configuration.domain;

import com.icthh.xm.commons.config.domain.Configuration;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class UpdateAliasTreeEvent extends ApplicationEvent {

    @Getter
    private final Configuration configuration;

    public UpdateAliasTreeEvent(Object source, Configuration configuration) {
        super(source);
        this.configuration = configuration;
    }
}
