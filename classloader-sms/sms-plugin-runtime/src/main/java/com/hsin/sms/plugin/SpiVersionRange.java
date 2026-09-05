package com.hsin.sms.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple SPI version range expression, e.g. {@code ">=1.2 <2.0"}.
 * Supported clauses: {@code >=x}, {@code >x}, {@code <x}, {@code <=x} separated by whitespace.
 */
public final class SpiVersionRange {

    private static final Pattern CLAUSE = Pattern.compile("(>=|<=|>|<)\\s*(\\d+(?:\\.\\d+)?)");

    private final List<Bound> bounds;

    private SpiVersionRange(List<Bound> bounds) {
        if (bounds.isEmpty()) {
            throw new IllegalArgumentException("empty version range");
        }
        this.bounds = List.copyOf(bounds);
    }

    /** Parses a whitespace-separated clause list such as {@code ">=1.2 <2.0"}. */
    public static SpiVersionRange parse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty version range");
        }
        Matcher m = CLAUSE.matcher(trimmed);
        List<Bound> parsed = new ArrayList<>();
        int cursor = 0;
        while (m.find()) {
            String gap = trimmed.substring(cursor, m.start());
            if (!gap.isBlank()) {
                throw new IllegalArgumentException("invalid version range: " + text);
            }
            String op = m.group(1);
            SpiVersion v = SpiVersion.parse(m.group(2));
            boolean inclusive = op.endsWith("=");
            boolean lower = op.startsWith(">");
            parsed.add(new Bound(lower, inclusive, v));
            cursor = m.end();
        }
        if (!trimmed.substring(cursor).isBlank()) {
            throw new IllegalArgumentException("invalid version range: " + text);
        }
        return new SpiVersionRange(parsed);
    }

    /** Builds a minimum-only inclusive range. */
    public static SpiVersionRange atLeast(String min) {
        return new SpiVersionRange(List.of(new Bound(true, true, SpiVersion.parse(min))));
    }

    /** Returns true when {@code version} satisfies every clause. */
    public boolean supports(SpiVersion version) {
        Objects.requireNonNull(version, "version");
        for (Bound b : bounds) {
            int cmp = version.compareTo(b.version);
            if (b.lower) {
                if (cmp < 0 || (cmp == 0 && !b.inclusive)) {
                    return false;
                }
            } else {
                if (cmp > 0 || (cmp == 0 && !b.inclusive)) {
                    return false;
                }
            }
        }
        return true;
    }

    private record Bound(boolean lower, boolean inclusive, SpiVersion version) {
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Bound b : bounds) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(b.lower ? '>' : '<');
            if (b.inclusive) {
                sb.append('=');
            }
            sb.append(b.version);
        }
        return sb.toString();
    }
}
