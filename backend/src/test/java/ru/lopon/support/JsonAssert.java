package ru.lopon.support;

import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;

public final class JsonAssert {

    private JsonAssert() {
    }

    public static void assertSimilar(JsonNode expected, JsonNode actual, Set<String> ignored) {
        List<String> problems = new ArrayList<>();
        compare("", expected, actual, ignored, problems);
        if (!problems.isEmpty()) {
            fail(problems.size() + " differences:\n" + String.join("\n", problems.subList(0, Math.min(40, problems.size()))));
        }
    }

    private static void compare(String path, JsonNode expected, JsonNode actual, Set<String> ignored, List<String> problems) {
        if (ignored.contains(path)) {
            return;
        }
        if (expected.isNumber() && actual.isNumber()) {
            double e = expected.asDouble();
            double a = actual.asDouble();
            if (Math.abs(e - a) > 1e-9 * Math.max(1, Math.abs(e))) {
                problems.add(path + ": expected " + expected + " but was " + actual);
            }
            return;
        }
        if (expected.isObject() && actual.isObject()) {
            Set<String> keys = new TreeSet<>(expected.propertyNames());
            keys.addAll(actual.propertyNames());
            for (String key : keys) {
                String child = path + "/" + key;
                if (ignored.contains(child)) {
                    continue;
                }
                if (!expected.has(key) || !actual.has(key)) {
                    problems.add(child + ": key present only in " + (expected.has(key) ? "expected" : "actual"));
                    continue;
                }
                compare(child, expected.get(key), actual.get(key), ignored, problems);
            }
            return;
        }
        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                problems.add(path + ": array size " + expected.size() + " vs " + actual.size());
                return;
            }
            for (int i = 0; i < expected.size(); i++) {
                compare(path + "/" + i, expected.get(i), actual.get(i), ignored, problems);
            }
            return;
        }
        if (!expected.equals(actual)) {
            problems.add(path + ": expected " + expected + " but was " + actual);
        }
    }
}
