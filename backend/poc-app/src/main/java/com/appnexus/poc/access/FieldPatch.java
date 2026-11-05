package com.appnexus.poc.access;

public sealed interface FieldPatch<T> permits FieldPatch.Unchanged, FieldPatch.Clear, FieldPatch.Replace {

    record Unchanged<T>() implements FieldPatch<T> {}

    record Clear<T>() implements FieldPatch<T> {}

    record Replace<T>(T value) implements FieldPatch<T> {
        public Replace {
            if (value == null) {
                throw new IllegalArgumentException("Replacement value cannot be null; use Clear instead");
            }
        }
    }
}
