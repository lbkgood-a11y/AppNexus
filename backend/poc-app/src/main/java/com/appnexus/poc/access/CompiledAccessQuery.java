package com.appnexus.poc.access;

import java.util.Map;

public record CompiledAccessQuery(String sql, Map<String, Object> parameters) {
}
