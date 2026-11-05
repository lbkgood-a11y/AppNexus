package com.appnexus.poc.cache;

import java.util.Objects;

public record PermissionSnapshot(String subjectId, PermissionVersion version) {
    public PermissionSnapshot {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(version, "version");
    }
}
