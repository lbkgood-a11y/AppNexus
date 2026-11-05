package com.appnexus.poc.access;

import java.util.Objects;

public record ComparisonPredicate(String fieldId, Operator operator, Object value) implements AccessPredicate {

    public ComparisonPredicate {
        Objects.requireNonNull(fieldId, "fieldId");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
    }

    public enum Operator {
        EQUALS,
        NOT_EQUALS
    }
}
