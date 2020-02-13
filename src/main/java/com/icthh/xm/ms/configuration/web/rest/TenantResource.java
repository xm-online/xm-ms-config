package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.permission.annotation.PrivilegeDescription;
import com.icthh.xm.ms.configuration.domain.TenantState;
import com.icthh.xm.ms.configuration.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(API_PREFIX)
public class TenantResource {

    private final TenantService tenantService;

    @PostMapping(value = "/tenants/{serviceName}")
    @Timed
    @PreAuthorize("hasPermission({'tenant':#tenant}, 'CONFIG.TENANT.CREATE')")
    @PrivilegeDescription("Privilege to add a new config tenant")
    public ResponseEntity<Void> addTenant(@PathVariable String serviceName, @RequestBody String tenantKey) {
        tenantService.addTenant(serviceName, tenantKey);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/tenants/{serviceName}/{tenantKey}")
    @Timed
    @PreAuthorize("hasPermission({'serviceName':#serviceName, 'tenantKey':#tenantKey, 'tenantState':#tenantState}, 'CONFIG.TENANT.UPDATE')")
    @PrivilegeDescription("Privilege to update config tenant")
    public ResponseEntity<Void> updateTenant(@PathVariable String serviceName, @PathVariable String tenantKey, @RequestBody String tenantState) {
        tenantService.updateTenant(serviceName, new TenantState(tenantKey, tenantState));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/tenants/{serviceName}")
    @Timed
    @PostFilter("hasPermission({'returnObject': filterObject, 'log': false}, 'CONFIG.TENANT.GET_LIST')")
    @PrivilegeDescription("Privilege to get all config tenants")
    public Set<TenantState> getTenants(@PathVariable String serviceName) {
        return tenantService.getTenants(serviceName);
    }

    @DeleteMapping("/tenants/{serviceName}/{tenantKey}")
    @Timed
    @PreAuthorize("hasPermission({'serviceName':#serviceName, 'tenantKey':#tenantKey}, 'CONFIG.TENANT.DELETE')")
    @PrivilegeDescription("Privilege to delete config tenant")
    public ResponseEntity<Void> deleteConfiguration(@PathVariable String serviceName, @PathVariable String tenantKey) {
        tenantService.deleteTenant(serviceName, tenantKey);
        return ResponseEntity.ok().build();
    }

}
