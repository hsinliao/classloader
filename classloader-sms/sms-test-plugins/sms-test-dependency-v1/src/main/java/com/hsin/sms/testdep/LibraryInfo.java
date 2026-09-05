package com.hsin.sms.testdep;

/**
 * Deliberately identical FQCN to {@code sms-test-dependency-v2}'s LibraryInfo.
 * Only its behavior differs, proving child-first dependency isolation.
 */
public final class LibraryInfo {

    public String version() {
        return "1.0";
    }

    public String feature() {
        return "vendor-v1-feature";
    }

    @Override
    public String toString() {
        return "LibraryInfo(v1)";
    }
}
