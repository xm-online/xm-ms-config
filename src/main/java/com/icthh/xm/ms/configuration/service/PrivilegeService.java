package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.getRequestSourceTypeLogName;

import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.commons.permission.config.PermissionProperties;
import com.icthh.xm.commons.permission.domain.Permission;
import com.icthh.xm.commons.permission.domain.Privilege;
import com.icthh.xm.commons.permission.domain.mapper.PermissionMapper;
import com.icthh.xm.commons.permission.domain.mapper.PrivilegeMapper;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.tenant.Tenant;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.domain.Configuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivilegeService {

    private static final String NONE_TENANT = "[no tenant]";

    private final PermissionProperties properties;
    private final ConfigurationService configurationService;
    private final TenantService tenantService;
    private final TenantContextHolder tenantContextHolder;
    private final XmRequestContextHolder requestContextHolder;

    /**
     * Update old privileges config with new.
     *
     * @param appName    the ms/app name
     * @param privileges the privileges from ms/app
     */
    @LoggingAspectConfig(inputExcludeParams = "privileges")
    public synchronized void updatePrivileges(String appName, Set<Privilege> privileges) {
        Objects.requireNonNull(appName, "appName can't be null");
        if (StringUtils.isBlank(appName)) {
            throw new IllegalArgumentException("appName can't be blank");
        }

        if (CollectionUtils.isEmpty(privileges)) {
            log.info("[{}] Privileges collection is empty, ignore update, app/ms: '{}'",
                     getRequestSourceTypeLogName(requestContextHolder),
                     appName);
        } else {
            if (updateAppPrivilegesConfig(appName, privileges)) {
                updateAppPermissionsConfig(appName, privileges);
                log.info("[{}] Privileges config was updated, app/ms: '{}'",
                         getRequestSourceTypeLogName(requestContextHolder), appName);
            }
        }
    }

    /**
     * Update application privileges config.
     *
     * @param appName          the application name (or ms name)
     * @param appPrivilegesNew not null and not empty collection of app new privileges
     * @return {@code true} if {@code appName} application privileges was updated
     */
    private boolean updateAppPrivilegesConfig(final String appName,
                                              final Set<Privilege> appPrivilegesNew) {
        Optional<String> commonPrivilegesYml = getConfig(null, properties.getPrivilegesSpecPath());

        // if common privileges config doesn't exist yet
        if (!commonPrivilegesYml.isPresent()) {
            Map<String, Collection<Privilege>> commonPrivileges = new TreeMap<>();
            commonPrivileges.put(appName, appPrivilegesNew);

            // first common privileges config creation (with only one app/ms privileges)
            createConfig(properties.getPrivilegesSpecPath(), PrivilegeMapper.privilegesMapToYml(commonPrivileges));
        } else {
            Map<String, Collection<Privilege>> currentPrivileges = parsePrivilegesConfig(commonPrivilegesYml.get());
            Collection<Privilege> appPrivilegesCurrent = currentPrivileges.get(appName);

            // check is privileges collection has changes for <appName> application ?
            if (appPrivilegesCurrent != null
                && CollectionUtils.isEqualCollection(appPrivilegesCurrent, appPrivilegesNew)) {
                log.info("[{}] Privileges are not modified, app/ms: '{}'",
                         getRequestSourceTypeLogName(requestContextHolder), appName);
            } else {
                // replace app privileges in current common config file
                currentPrivileges.put(appName, appPrivilegesNew);

                return updateConfig(null,
                                    properties.getPrivilegesSpecPath(),
                                    PrivilegeMapper.privilegesMapToYml(currentPrivileges));
            }
        }

        return false;
    }

    private static Map<String, Collection<Privilege>> parsePrivilegesConfig(String yml) {
        // TreeMap for editable map instance and key sorting (ymlToPrivileges returns unmodifiable map)
        return new TreeMap<>(PrivilegeMapper.ymlToPrivileges(yml));
    }

    private void updateAppPermissionsConfig(String appName, Set<Privilege> newPrivileges) {
        // Get new privileges keys
        List<String> newPrivilegeKeys = newPrivileges.stream().map(Privilege::getKey).collect(Collectors.toList());

        // for each tenant update his permissions.yml
        tenantService.getTenants(appName).forEach(tenantState -> {
            Tenant currentTenant = TenantContextUtils.buildTenant(tenantState.getName());
            // execute in tenant context
            tenantContextHolder.getPrivilegedContext().execute(currentTenant,
                                                               () ->
                                                                   updateTenantPermissions(appName,
                                                                                           currentTenant.getTenantKey(),
                                                                                           newPrivilegeKeys));
        });
    }

    private void updateTenantPermissions(String appName, TenantKey tenantKey, List<String> newPrivilegeKeys) {
        log.info("[{}] Updating permissions for tenant: '{}', app/ms: '{}'",
                 getRequestSourceTypeLogName(requestContextHolder), tenantKey.getValue(), appName);

        // get permissions config text
        Optional<String> oldPermissionsYml = getConfig(tenantKey.getValue(), properties.getPermissionsSpecPath());

        // if yaml text exist and not blank
        oldPermissionsYml.filter(StringUtils::isNotBlank).ifPresent(oldPermissionsYmlData -> {
            // parse permissions.yml to map
            Map<String, Permission> oldPermissions = PermissionMapper.ymlToPermissions(oldPermissionsYmlData);

            syncPermissionsWithPrivileges(tenantKey.getValue(), appName, newPrivilegeKeys, oldPermissions);
        });
    }


    private void syncPermissionsWithPrivileges(String tenantKeyValue,
                                               String appName,
                                               List<String> newPrivilegeKeys,
                                               Map<String, Permission> oldPermissions) {
        // for each value (permission) perform closure and collect permissions as set
        Collection<Permission> newPermissions = oldPermissions.values().stream()
            .peek(syncPermission(appName, newPrivilegeKeys)).collect(Collectors.toSet());

        updateConfig(tenantKeyValue,
                     properties.getPermissionsSpecPath(),
                     PermissionMapper.permissionsToYml(newPermissions));
    }

    private Consumer<Permission> syncPermission(String appName, List<String> newPrivilegeKeys) {
        return permission -> {
            // check permissions only for specified app
            if (appName.equalsIgnoreCase(permission.getMsName())) {
                // for non existed privileges mark permission as deleted
                boolean isPrivilegeDeleted = !newPrivilegeKeys.contains(permission.getPrivilegeKey());
                permission.setDeleted(isPrivilegeDeleted);
            }
        };
    }

    private Optional<String> getConfig(String tenantKeyValue, String path) {
        try {
            return configurationService.findConfiguration(applyTenant(path, tenantKeyValue))
                .map(Configuration::getContent);
        } catch (Exception e) {
            log.error("[{}] Error getting configuration '{}' for tenant '{}'",
                      getRequestSourceTypeLogName(requestContextHolder),
                      path, StringUtils.defaultIfBlank(tenantKeyValue, NONE_TENANT), e);
        }
        return Optional.empty();
    }

    private boolean updateConfig(String tenantKeyValue, String path, String content) {
        try {
            configurationService.updateConfiguration(new Configuration(applyTenant(path, tenantKeyValue), content));
            log.info("[{}] Config '{}' updated for tenant '{}'",
                     getRequestSourceTypeLogName(requestContextHolder),
                     path, StringUtils.defaultIfBlank(tenantKeyValue, NONE_TENANT));
            return true;
        } catch (Exception e) {
            log.error("[{}] Error updating configuration '{}' for tenant '{}'",
                      getRequestSourceTypeLogName(requestContextHolder),
                      path, StringUtils.defaultIfBlank(tenantKeyValue, NONE_TENANT), e);
        }
        return false;
    }

    private void createConfig(String path, String content) {
        try {
            configurationService.createConfiguration(new Configuration(path, content));
            log.info("[{}] Config created '{}'", getRequestSourceTypeLogName(requestContextHolder), path);
        } catch (Exception e) {
            log.error("[{}] Error creating configuration '{}'", getRequestSourceTypeLogName(requestContextHolder),
                      path, e);
        }
    }

    private static String applyTenant(String path, String tenant) {
        if (StringUtils.isBlank(tenant)) {
            return path;
        }
        return StringUtils.replaceAll(path, "\\{tenantName\\}", tenant.toUpperCase());
    }

}
