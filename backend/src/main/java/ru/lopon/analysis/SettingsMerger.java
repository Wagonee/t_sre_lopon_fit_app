package ru.lopon.analysis;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class SettingsMerger {

    private SettingsMerger() {
    }

    public static AnalysisSettings mergeWithDefaults(JsonMapper mapper, JsonNode overrides) {
        ObjectNode base = mapper.valueToTree(AnalysisSettings.DEFAULTS);
        if (overrides != null && overrides.isObject()) {
            merge(base, overrides);
        }
        return mapper.treeToValue(base, AnalysisSettings.class);
    }

    private static void merge(ObjectNode target, JsonNode source) {
        source.properties().forEach(entry -> {
            JsonNode current = target.get(entry.getKey());
            if (entry.getValue().isObject() && current != null && current.isObject()) {
                merge((ObjectNode) current, entry.getValue());
            } else {
                target.set(entry.getKey(), entry.getValue());
            }
        });
    }
}
