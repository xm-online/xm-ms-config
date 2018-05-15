package com.icthh.xm.ms.configuration.domain;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Comparator;

@Value
@EqualsAndHashCode(of = "name")
public class ConfigVersion implements Comparable<ConfigVersion> {

    private String name;
    private int commitTime;

    @Override
    public int compareTo(ConfigVersion other) {
        return Comparator.comparing(ConfigVersion::getCommitTime).compare(this, other);
    }
}
