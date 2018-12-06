package com.icthh.xm.ms.configuration.web.rest;

import com.icthh.xm.ms.configuration.config.DefaultProfileUtil;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resource to return information about the currently running Spring profiles.
 */
@RestController
@RequestMapping("/api")
public class ProfileInfoResource {

    private final Environment env;

    public ProfileInfoResource(Environment env) {
        this.env = env;
    }

    @GetMapping("/profile-info")
    @PostAuthorize("hasPermission({'returnObject': returnObject}, 'CONFIG.PROFILE_INFO.GET_LIST.ITEM')")
    public ProfileInfoVM getActiveProfiles() {
        String[] activeProfiles = DefaultProfileUtil.getActiveProfiles(env);
        return new ProfileInfoVM(activeProfiles);
    }

    static class ProfileInfoVM {

        private String[] activeProfiles;

        ProfileInfoVM(String[] activeProfiles) {
            this.activeProfiles = activeProfiles;
        }

        public String[] getActiveProfiles() {
            return activeProfiles;
        }
    }
}
