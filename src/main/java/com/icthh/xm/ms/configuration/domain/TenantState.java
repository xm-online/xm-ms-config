package com.icthh.xm.ms.configuration.domain;

import java.util.Objects;
import lombok.Value;

@Value
public class TenantState {
    private String name;
    private String state;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TenantState tenant = (TenantState) o;
        return !(tenant.getName() == null || getName() == null) && Objects.equals(getName(), tenant.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName());
    }
}
