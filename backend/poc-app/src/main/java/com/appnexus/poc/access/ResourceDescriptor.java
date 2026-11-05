package com.appnexus.poc.access;

import java.util.Map;
import java.util.Objects;

public record ResourceDescriptor(String tableName, String alias, String tenantColumn, Map<String, String> fields) {

    public ResourceDescriptor {
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(tenantColumn, "tenantColumn");
        fields = Map.copyOf(fields);
    }

    public String column(String fieldId) {
        String column = fields.get(fieldId);
        if (column == null) {
            throw new IllegalArgumentException("Unregistered access field: " + fieldId);
        }
        return alias + "." + column;
    }

    public String tenantColumnReference() {
        return alias + "." + tenantColumn;
    }
}
