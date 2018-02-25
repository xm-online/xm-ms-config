package com.icthh.xm.ms.configuration.security.access;

import com.icthh.xm.commons.permission.access.ResourceFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfigResourceFactory implements ResourceFactory {

    @Override
    public Object getResource(Object resourceId, String objectType) {
        return null;
    }
}
