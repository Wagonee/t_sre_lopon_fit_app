package ru.lopon.fit;

public record FitRecord(
        long timestamp,
        Integer heartRate,
        Double speed,
        Double altitude,
        Integer cadence,
        Integer power,
        Integer temperature,
        Double distance,
        Integer positionLat,
        Integer positionLong) {
}
