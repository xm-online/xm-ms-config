package com.icthh.xm.ms.configuration.service.filter;

import com.icthh.xm.commons.permission.service.filter.PermissionMsNameFilter;
import org.springframework.stereotype.Component;

@Component("permissionMsNameFilter")
public class AlwaysTruePermissionMsNameFilter implements PermissionMsNameFilter {

    @Override
    public boolean filterPermission(String permissionMsName) {
        return true;
    }
}
