package com.icthh.xm.ms.configuration.security;

import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.ms.configuration.config.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * Implementation of AuditorAware based on Spring Security.
 */
@Component
@RequiredArgsConstructor
public class SpringSecurityAuditorAware implements AuditorAware<String> {

    private final XmAuthenticationContextHolder authenticationContextHolder;

    @Override
    public String getCurrentAuditor() {
        return authenticationContextHolder.getContext().getLogin().orElse(Constants.SYSTEM_ACCOUNT);
    }
}
