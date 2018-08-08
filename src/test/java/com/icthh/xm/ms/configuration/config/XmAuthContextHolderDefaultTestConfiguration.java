package com.icthh.xm.ms.configuration.config;

import static com.icthh.xm.commons.i18n.I18nConstants.LANGUAGE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

/**
 * The {@link XmAuthContextHolderDefaultTestConfiguration} class.
 */
@Configuration
public class XmAuthContextHolderDefaultTestConfiguration {

    @Bean
    @Primary
    public XmAuthenticationContextHolder authenticationContextHolder() {
        XmAuthenticationContext context = mock(XmAuthenticationContext.class);
        when(context.hasAuthentication()).thenReturn(true);
        when(context.getLogin()).thenReturn(Optional.of("testLogin"));
        when(context.getUserKey()).thenReturn(Optional.of("ACCOUNT.TEST"));
        when(context.getDetailsValue(LANGUAGE)).thenReturn(Optional.of("en"));

        XmAuthenticationContextHolder holder = mock(XmAuthenticationContextHolder.class);
        when(holder.getContext()).thenReturn(context);

        return holder;
    }

}
