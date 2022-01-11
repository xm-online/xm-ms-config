package com.icthh.xm.ms.configuration.config.lep;


import com.icthh.xm.commons.lep.BaseProceedingLep;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.tenant.TenantContext;

public class LepContext {

    public Object commons;
    public Object inArgs;
    public BaseProceedingLep lep;
    public XmAuthenticationContext authContext;
    public TenantContext tenantContext;
    public Object methodResult;

}

