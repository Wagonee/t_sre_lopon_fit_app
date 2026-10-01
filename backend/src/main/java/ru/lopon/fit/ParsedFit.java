package ru.lopon.fit;

import java.util.List;

public record ParsedFit(FitSession session, String manufacturer, List<FitRecord> records) {
}
