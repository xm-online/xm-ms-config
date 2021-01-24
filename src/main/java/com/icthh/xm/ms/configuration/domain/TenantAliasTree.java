package com.icthh.xm.ms.configuration.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static com.icthh.xm.ms.configuration.domain.TenantAliasTree.TraverseRule.CONTINUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNullElse;

@Slf4j
public class TenantAliasTree {

    @Getter @Setter
    private List<TenantAlias> tenantAliasTree = emptyList();
    @Getter
    @JsonIgnore
    private Map<String, TenantAlias> tenants = emptyMap();
    @JsonIgnore
    private Map<String, List<TenantAlias>> parents = emptyMap();

    public void init() {
        tenantAliasTree = unmodifiableList(tenantAliasTree);
        tenants = unmodifiableMap(tenants());
        parents = unmodifiableMap(parents());
    }

    public List<TenantAlias> getParents(String tenant) {
        return parents.getOrDefault(tenant, emptyList());
    }

    @Data
    @ToString(exclude = "children")
    public static class TenantAlias {
        private String key;
        private List<TenantAlias> children;
        private Set<String> mergeAsYml;
        @JsonIgnore
        private TenantAlias parent;

        public void traverseChild(BiFunction<TenantAlias, TenantAlias, TraverseRule> operation) {
            List<TenantAlias> children = requireNonNullElse(this.children, emptyList());
            for (var child: children) {
                TraverseRule rule = operation.apply(this, child);
                if (rule == CONTINUE) {
                    child.traverseChild(operation);
                }
            }
        }
    }

    public void traverse(BiFunction<TenantAlias, TenantAlias, TraverseRule> operation) {
        List<TenantAlias> tree = requireNonNullElse(tenantAliasTree, emptyList());
        tree.forEach(node -> node.traverseChild(operation));
    }


    private Map<String, List<TenantAlias>> parents() {
        Map<String, List<TenantAlias>> parents = new HashMap<>();
        traverse((parent, child) -> {
            child.parent = parent;

            List<TenantAlias> parentsList = new ArrayList<>();
            TenantAlias currentNode = child;
            while (currentNode.parent != null) {
                parentsList.add(currentNode.parent);
                currentNode = currentNode.parent;
            }

            parents.put(child.getKey(), parentsList);
            return CONTINUE;
        });
        return parents;
    }

    private Map<String, TenantAlias> tenants() {
        Map<String, TenantAlias> tenants = new HashMap<>();
        traverse((parent, child) -> {
            tenants.put(parent.getKey(), parent);
            if (tenants.containsKey(child.getKey())) {
                log.error("Key {} present twice in tenant alias configuration", child.getKey());
                throw new WrongTenantAliasConfiguration(child.getKey() + " present twice in tenant alias configuration");
            }
            tenants.put(child.getKey(), child);
            return CONTINUE;
        });
        return tenants;
    }

    public enum TraverseRule {
        BREAK, CONTINUE;
    }

    public static class WrongTenantAliasConfiguration extends RuntimeException {
        public WrongTenantAliasConfiguration(String message) {
            super(message);
        }
    }
}

