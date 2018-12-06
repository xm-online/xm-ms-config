package com.icthh.xm.ms.configuration.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Objects;

@Value
public class TenantState {
    private String name;
    private String state;

    @JsonCreator
    public TenantState(@JsonProperty("name") String name, @JsonProperty("state") String state) {
        this.name = name;
        this.state = state;
    }

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
