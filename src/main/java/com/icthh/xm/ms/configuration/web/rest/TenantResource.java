package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.ms.configuration.domain.TenantState;
import com.icthh.xm.ms.configuration.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(API_PREFIX)
public class TenantResource {

    private final TenantService tenantService;

    @PostMapping(value = "/tenants/{serviceName}")
    @Timed
    public ResponseEntity<Void> addTenant(@PathVariable String serviceName, @RequestBody String tenantKey) {
        tenantService.addTenant(serviceName, tenantKey);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/tenants/{serviceName}/{tenantKey}")
    @Timed
    public ResponseEntity<Void> updateTenant(@PathVariable String serviceName, @PathVariable String tenantKey, @RequestBody String tenantState) {
        tenantService.updateTenant(serviceName, new TenantState(tenantKey, tenantState));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/tenants/{serviceName}")
    @Timed
    public ResponseEntity<Set<TenantState>> getTenants(@PathVariable String serviceName) {
        return ResponseEntity.ok().body(tenantService.getTenants(serviceName));
    }

    @DeleteMapping("/tenants/{serviceName}/{tenantKey}")
    @Timed
    public ResponseEntity<Void> deleteConfiguration(@PathVariable String serviceName, @PathVariable String tenantKey) {
        tenantService.deleteTenant(serviceName, tenantKey);
        return ResponseEntity.ok().build();
    }

}
