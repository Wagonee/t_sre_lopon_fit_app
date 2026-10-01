package ru.lopon.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import ru.lopon.analysis.AnalysisResult.Decoupling;
import ru.lopon.analysis.AnalysisResult.Half;
import ru.lopon.analysis.AnalysisResult.Segment;

final class DecouplingCalculator {

    private final AnalysisSettings.Analysis a;
    private final boolean indoor;
    private final boolean hasPower;
    private final String tempCtx;
    private final TreeMap<Integer, Minute> minutes = new TreeMap<>();

    DecouplingCalculator(AnalysisSettings s, int n, Integer[] hr, Double[] spd, Double[] altS, Integer[] pwr, double[] dts,
                         boolean indoor, boolean hasPower, String tempCtx) {
        this.a = s.analysis();
        this.indoor = indoor;
        this.hasPower = hasPower;
        this.tempCtx = tempCtx;
        double acc = 0.0;
        for (int i = 0; i < n; i++) {
            acc += dts[i];
            double altitude = altS[i];
            Minute m = minutes.computeIfAbsent((int) Math.floor(acc / 60), k -> new Minute(altitude));
            if (WorkoutAnalyzer.truthy(hr[i])) {
                m.hr.add(hr[i]);
            }
            if (spd[i] != null) {
                m.speed.add(spd[i] * 3.6);
            }
            if (WorkoutAnalyzer.truthy(pwr[i])) {
                m.power.add(pwr[i]);
            }
            m.lastAltitude = altitude;
        }
    }

    Decoupling calculate() {
        int skip = (int) a.skipFirstMinutes();
        List<Integer> keys = minutes.keySet().stream().filter(k -> k >= skip).toList();
        if (keys.size() < 2 * 8) {
            return Decoupling.none("Заезд слишком короткий для оценки дрейфа.");
        }
        if (indoor || hasPower) {
            int half = keys.size() / 2;
            Ratio r1 = ratio(keys.subList(0, half), hasPower);
            Ratio r2 = ratio(keys.subList(half, keys.size()), hasPower);
            if (r1 == null || r2 == null) {
                return Decoupling.none("Недостаточно данных.");
            }
            return result(hasPower ? "whole_ride_power" : "whole_ride_speed",
                    new Segment(keys.getFirst(), keys.getLast(), keys.size()), r1, r2, hasPower ? "Вт" : "км/ч");
        }
        int bestFrom = 0;
        int bestTo = -1;
        int[] cur = null;
        for (int idx = 0; idx < keys.size(); idx++) {
            int k = keys.get(idx);
            Minute m = minutes.get(k);
            boolean flat = Math.abs(m.lastAltitude - m.firstAltitude) < a.flatNetClimbMPerMin();
            boolean fast = !m.speed.isEmpty() && Py.mean(m.speed) > a.flatMinSpeedKmh();
            boolean good = flat && fast && !m.hr.isEmpty();
            if (good && (cur == null || k == keys.get(idx - 1) + 1)) {
                cur = cur == null ? new int[] {idx, idx} : new int[] {cur[0], idx};
                if (cur[1] - cur[0] > bestTo - bestFrom) {
                    bestFrom = cur[0];
                    bestTo = cur[1];
                }
            } else {
                cur = good ? new int[] {idx, idx} : null;
            }
        }
        int length = bestTo - bestFrom + 1;
        if (length < (int) a.flatMinMinutes()) {
            return new Decoupling("none", null, null, null, null, null,
                    "Ровного сегмента ≥" + Py.str(a.flatMinMinutes()) + " мин нет (самый длинный " + Math.max(length, 0)
                            + " мин) — маршрут холмистый, дрейф честно не считаем.",
                    Math.max(length, 0), false);
        }
        List<Integer> seg = keys.subList(bestFrom, bestTo + 1);
        int half = length / 2;
        Ratio r1 = ratio(seg.subList(0, half), false);
        Ratio r2 = ratio(seg.subList(half, seg.size()), false);
        if (r1 == null || r2 == null) {
            return Decoupling.none("Недостаточно данных.");
        }
        return result("flat_segment", new Segment(seg.getFirst(), seg.getLast(), length), r1, r2, "км/ч");
    }

    private Decoupling result(String method, Segment segment, Ratio r1, Ratio r2, String unit) {
        double pct = Py.round((r1.ratio - r2.ratio) / r1.ratio * 100, 1);
        return new Decoupling(method, segment, new Half(Py.round(r1.value, 1), Py.round(r1.hr)),
                new Half(Py.round(r2.value, 1), Py.round(r2.hr)), unit, pct, verdict(pct), null, false);
    }

    private Ratio ratio(List<Integer> keys, boolean usePower) {
        List<Double> num = new ArrayList<>();
        List<Double> den = new ArrayList<>();
        for (int k : keys) {
            Minute m = minutes.get(k);
            List<? extends Number> values = usePower ? m.power : m.speed;
            if (!m.hr.isEmpty() && !values.isEmpty()) {
                num.add(Py.mean(values));
                den.add(Py.mean(m.hr));
            }
        }
        if (num.isEmpty()) {
            return null;
        }
        double meanNum = Py.mean(num);
        double meanDen = Py.mean(den);
        return new Ratio(meanNum / meanDen, meanNum, meanDen);
    }

    private String verdict(double pct) {
        boolean hot = "hot".equals(tempCtx);
        double good = a.decouplingGoodPct() + (hot ? a.heatAllowancePp() : 0);
        String drift = "Дрейф " + Py.format(pct, 1, true) + "%";
        if (pct <= good) {
            return drift + " — хорошо (порог " + Py.format(good, 0, false) + "%" + (hot ? ", с поправкой на жару" : "") + ").";
        }
        return drift + " выше порога " + Py.format(good, 0, false) + "% — усталость, недозаправка или условия (жара/ветер).";
    }

    private record Ratio(double ratio, double value, double hr) {
    }

    private static final class Minute {
        final List<Integer> hr = new ArrayList<>();
        final List<Double> speed = new ArrayList<>();
        final List<Integer> power = new ArrayList<>();
        final double firstAltitude;
        double lastAltitude;

        Minute(double altitude) {
            firstAltitude = altitude;
            lastAltitude = altitude;
        }
    }
}
