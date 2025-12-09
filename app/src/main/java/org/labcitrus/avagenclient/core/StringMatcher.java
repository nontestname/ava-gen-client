package org.labcitrus.avagenclient.core;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class StringMatcher implements Predicate<String> {
    private final Predicate<String> predicate;
    private final String label;

    private StringMatcher(Predicate<String> predicate, String label) {
        this.predicate = predicate;
        this.label = label;
    }

    private static String s(String v) { return v == null ? null : v; }

    @Override
    public boolean test(String value) { return value != null && predicate.test(value); }

    @Override
    public String toString() { return label; }

    // ---- Factories ----

    public static StringMatcher equalsIgnoreCase(String needle) {
        String n = needle == null ? "" : needle;
        return new StringMatcher(v -> v.equalsIgnoreCase(n),
                "equalsIgnoreCase(\"" + esc(n) + "\")");
    }

    public static StringMatcher containsIgnoreCase(String needle) {
        String n = needle == null ? "" : needle;
        String low = n.toLowerCase();
        return new StringMatcher(v -> v.toLowerCase().contains(low),
                "containsIgnoreCase(\"" + esc(n) + "\")");
    }

    // Alias to match your DSL name exactly
    public static StringMatcher containsStringIgnoringCase(String needle) {
        return containsIgnoreCase(needle);
    }

    // NEW: Case-sensitive contains
    public static StringMatcher containsString(String needle) {
        String n = needle == null ? "" : needle;
        return new StringMatcher(
                v -> v != null && v.contains(n),
                "containsString(\"" + esc(n) + "\")"
        );
    }

    public static StringMatcher startsWithIgnoreCase(String needle) {
        String n = Objects.toString(needle, "");
        String low = n.toLowerCase();
        return new StringMatcher(v -> v.toLowerCase().startsWith(low),
                "startsWithIgnoreCase(\"" + esc(n) + "\")");
    }

    public static StringMatcher endsWithIgnoreCase(String needle) {
        String n = Objects.toString(needle, "");
        String low = n.toLowerCase();
        return new StringMatcher(v -> v.toLowerCase().endsWith(low),
                "endsWithIgnoreCase(\"" + esc(n) + "\")");
    }

    public static StringMatcher regex(String pattern) {
        Pattern p = Pattern.compile(Objects.toString(pattern, ""), Pattern.DOTALL);
        return new StringMatcher(v -> p.matcher(v).find(), "regex(\"" + esc(pattern) + "\")");
    }

    public static StringMatcher regex(Pattern pattern) {
        Pattern p = pattern == null ? Pattern.compile("") : pattern;
        return new StringMatcher(v -> p.matcher(v).find(), "regex(" + p + ")");
    }

    private static String esc(String s) { return s == null ? "" : s.replace("\"", "\\\""); }
}