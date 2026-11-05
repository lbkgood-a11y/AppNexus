package com.appnexus.poc.access;

public enum DisclosureLevel {
    HIDDEN,
    REPLACEMENT,
    MASKED,
    ORIGINAL;

    public static DisclosureLevel leastRevealing(DisclosureLevel left, DisclosureLevel right) {
        return left.ordinal() <= right.ordinal() ? left : right;
    }

    public static DisclosureLevel mostRevealing(DisclosureLevel left, DisclosureLevel right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
