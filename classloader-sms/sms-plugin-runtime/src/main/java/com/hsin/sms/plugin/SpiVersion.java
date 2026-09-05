package com.hsin.sms.plugin;

import java.util.Objects;

/**
 * A two-part SPI version ({@code major.minor}), used for compatibility decisions.
 * Plugins and the runtime only ever share a class for the same major version.
 */
public final class SpiVersion implements Comparable<SpiVersion> {

    private final int major;
    private final int minor;

    private SpiVersion(int major, int minor) {
        if (major < 1 || minor < 0) {
            throw new IllegalArgumentException("invalid spi version " + major + "." + minor);
        }
        this.major = major;
        this.minor = minor;
    }

    public static SpiVersion of(int major, int minor) {
        return new SpiVersion(major, minor);
    }

    /** Parses {@code "1.2"}, {@code "1.2.3"} or {@code "1"} (extra parts ignored). */
    public static SpiVersion parse(String text) {
        Objects.requireNonNull(text, "text");
        String v = text.trim();
        int dot = v.indexOf('.');
        try {
            if (dot < 0) {
                return new SpiVersion(Integer.parseInt(v), 0);
            }
            int major = Integer.parseInt(v.substring(0, dot));
            String minorText = v.substring(dot + 1);
            if (minorText.contains(".")) {
                minorText = minorText.substring(0, minorText.indexOf('.'));
            }
            return new SpiVersion(major, Integer.parseInt(minorText));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a valid SPI version: " + text);
        }
    }

    public int major() {
        return major;
    }

    /** Minor version; the contract treats minor as backward-compatible additions. */
    public int minor() {
        return minor;
    }

    /** Whether this version is greater than or equal to {@code other}. */
    public boolean isAtLeast(SpiVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(SpiVersion other) {
        Objects.requireNonNull(other, "other");
        int byMajor = Integer.compare(major, other.major);
        return byMajor != 0 ? byMajor : Integer.compare(minor, other.minor);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SpiVersion v && major == v.major && minor == v.minor;
    }

    @Override
    public int hashCode() {
        return 31 * major + minor;
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
