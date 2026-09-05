package com.hsin.sms.testdep;

/**
 * Deliberately identical FQCN to {@code sms-test-dependency-v1}'s LibraryInfo.
 * Only its behavior differs, proving child-first dependency isolation.
 */
public final class LibraryInfo {

    public String version() {
        return "2.0";
    }

    public String feature() {
        return "vendor-v2-feature";
    }

    @Override
    public String toString() {
        return "LibraryInfo(v2)";
    }
}
