package ru.lopon.fit;

public record FitSession(
        String sport,
        String subSport,
        Double totalDistance,
        Integer thresholdPower,
        Integer totalAscent,
        Integer totalCalories) {
}
