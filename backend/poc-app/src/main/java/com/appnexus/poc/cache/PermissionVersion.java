package com.appnexus.poc.cache;

public record PermissionVersion(long tenant, long subject, long mandatoryRestriction, long resource, long entitlement) {
}
