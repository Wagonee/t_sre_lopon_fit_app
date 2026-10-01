package ru.lopon.analysis;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import ru.lopon.analysis.AnalysisResult.AltitudeHalf;
import ru.lopon.analysis.AnalysisResult.Decoupling;
import ru.lopon.analysis.AnalysisResult.Effort;
import ru.lopon.analysis.AnalysisResult.ErgBlock;
import ru.lopon.analysis.AnalysisResult.HalvesAltitude;
import ru.lopon.analysis.AnalysisResult.Meta;
import ru.lopon.analysis.AnalysisResult.Series;
import ru.lopon.analysis.AnalysisResult.SettingsUsed;
import ru.lopon.analysis.AnalysisResult.Summary;
import ru.lopon.analysis.AnalysisResult.Temp;
import ru.lopon.analysis.AnalysisResult.Z2BandShare;
import ru.lopon.analysis.AnalysisResult.Zone;
import ru.lopon.fit.FitParseException;
import ru.lopon.fit.FitRecord;
import ru.lopon.fit.FitSession;
import ru.lopon.fit.ParsedFit;

public final class WorkoutAnalyzer {

    private static final List<String> POWER_ZONE_NAMES =
            List.of("Z1 акт. восст.", "Z2 выносл.", "Z3 темп", "Z4 порог", "Z5 VO2max", "Z6 анаэроб", "Z7 нейромыш.");
    private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final double SEMICIRCLE_DEGREES = 180.0 / Math.pow(2, 31);

    private final AnalysisSettings s;
    private final AnalysisSettings.Analysis a;
    private final FitSession session;
    private final int n;
    private final long[] ts;
    private final Integer[] hr;
    private final Double[] spd;
    private final Double[] alt;
    private final Integer[] cad;
    private final Integer[] pwr;
    private final Integer[] tmp;
    private final Double[] dist;
    private final Integer[] lat;
    private final Integer[] lon;
    private final double[] dts;

    private WorkoutAnalyzer(ParsedFit fit, AnalysisSettings settings) {
        List<FitRecord> recs = fit.records();
        if (recs.isEmpty()) {
            throw new FitParseException("В файле нет записей record");
        }
        s = settings;
        a = settings.analysis();
        session = fit.session();
        n = recs.size();
        ts = recs.stream().mapToLong(FitRecord::timestamp).toArray();
        hr = recs.stream().map(FitRecord::heartRate).toArray(Integer[]::new);
        spd = recs.stream().map(FitRecord::speed).toArray(Double[]::new);
        alt = recs.stream().map(FitRecord::altitude).toArray(Double[]::new);
        cad = recs.stream().map(FitRecord::cadence).toArray(Integer[]::new);
        pwr = recs.stream().map(FitRecord::power).toArray(Integer[]::new);
        tmp = recs.stream().map(FitRecord::temperature).toArray(Integer[]::new);
        dist = recs.stream().map(FitRecord::distance).toArray(Double[]::new);
        lat = recs.stream().map(FitRecord::positionLat).toArray(Integer[]::new);
        lon = recs.stream().map(FitRecord::positionLong).toArray(Integer[]::new);
        dts = new double[n];
        for (int i = 1; i < n; i++) {
            double d = ts[i] - ts[i - 1];
            dts[i] = d > 0 && d <= 6 ? d : 0.0;
        }
    }

    public static AnalysisResult analyze(ParsedFit fit, AnalysisSettings settings, String filename) {
        return new WorkoutAnalyzer(fit, settings).run(filename, fit.manufacturer());
    }

    public static List<Integer> hrZoneBounds(AnalysisSettings settings) {
        AnalysisSettings.HrZones z = settings.hrZones();
        if ("manual".equals(z.mode())) {
            return z.manualBounds().stream().map(b -> (int) b.doubleValue()).toList();
        }
        double hrMax = settings.athlete().hrMax();
        return z.pct().stream().map(p -> (int) Py.round(hrMax * p)).toList();
    }

    private AnalysisResult run(String filename, String device) {
        boolean hasGps = Arrays.stream(lat).anyMatch(Objects::nonNull);
        boolean hasPower = Arrays.stream(pwr).anyMatch(p -> p != null && p > 0);
        boolean hasHr = Arrays.stream(hr).anyMatch(Objects::nonNull);
        String sub = session.subSport() == null ? "" : session.subSport();
        boolean indoor = !hasGps || sub.contains("indoor") || sub.contains("virtual");

        double totalSec = Py.sum(dts);
        double mvThr = a.movingSpeedKmh() / 3.6;
        double movingSec = Py.sum(IntStream.range(0, n).filter(i -> spd[i] != null && spd[i] > mvThr).mapToDouble(i -> dts[i]).toArray());
        double stoppedSec = totalSec - movingSec;

        List<Integer> bounds = hrZoneBounds(s);
        List<String> names = s.hrZones().names();
        List<Double> coef = s.hrZones().loadCoef();
        double[] zsec = new double[5];
        AnalysisSettings.Z2Band z2b = s.z2Band();
        double z2bandSec = 0.0;
        for (int i = 0; i < n; i++) {
            Integer h = hr[i];
            if (h == null || dts[i] == 0) {
                continue;
            }
            int zi = (int) bounds.stream().filter(b -> h >= b).count();
            zsec[zi] += dts[i];
            if (z2b.lo() <= h && h <= z2b.hi()) {
                z2bandSec += dts[i];
            }
        }
        double hrSecSum = Py.sum(zsec);
        double hrSec = hrSecSum == 0 ? 1.0 : hrSecSum;
        List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            zones.add(new Zone(names.get(i), i > 0 ? bounds.get(i - 1) : 0, i < 4 ? bounds.get(i) : null,
                    Py.round(zsec[i] / 60, 1), Py.round(100 * zsec[i] / hrSec, 1)));
        }
        double load = Py.sum(IntStream.range(0, 5).mapToDouble(i -> zsec[i] / 60 * coef.get(i)).toArray());

        List<Integer> hrs = Arrays.stream(hr).filter(Objects::nonNull).toList();
        Long hrAvg = hrs.isEmpty() ? null : Py.round(Py.mean(hrs));
        Integer hrMax = hrs.isEmpty() ? null : hrs.stream().max(Integer::compare).orElseThrow();
        Double hrMaxSpeed = null;
        boolean hrArtifact = false;
        if (!hrs.isEmpty()) {
            int iMax = 0;
            for (int i = 1; i < n; i++) {
                if (orZero(hr[i]) > orZero(hr[iMax])) {
                    iMax = i;
                }
            }
            Double v = spd[iMax];
            hrMaxSpeed = v != null && v != 0 ? Py.round(v * 3.6, 1) : null;
            hrArtifact = hrMaxSpeed != null && hrMaxSpeed > a.hrArtifactSpeedKmh();
        }

        double lastDist = dist[n - 1] != null ? dist[n - 1] : 0.0;
        double totalDistM = truthy(session.totalDistance()) ? session.totalDistance() : lastDist;
        double avgSpeed = movingSec != 0 ? totalDistM / movingSec * 3.6 : 0.0;
        double maxSpeed = Arrays.stream(spd).filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(0) * 3.6;

        List<Integer> cads = IntStream.range(0, n)
                .filter(i -> cad[i] != null && cad[i] > 0 && spd[i] != null && spd[i] > mvThr)
                .mapToObj(i -> cad[i]).toList();
        Long cadAvg = cads.isEmpty() ? null : Py.round(Py.mean(cads));
        Integer cadMax = cads.isEmpty() ? null : cads.stream().max(Integer::compare).orElseThrow();
        Long cad80 = cads.isEmpty() ? null : Py.round(100.0 * cads.stream().filter(c -> c >= 80).count() / cads.size());
        Long cad90 = cads.isEmpty() ? null : Py.round(100.0 * cads.stream().filter(c -> c >= 90).count() / cads.size());

        Double[] altS = Py.rollingMean(Py.forwardFill(alt, 0.0), a.altitudeSmoothing());
        double ascent = climb(altS, 1, n, true);
        double descent = climb(altS, 1, n, false);
        int half = n / 2;
        HalvesAltitude halves = new HalvesAltitude(altitudeHalf(altS, 0, half), altitudeHalf(altS, half, n));

        List<Integer> temps = Arrays.stream(tmp).filter(Objects::nonNull).toList();
        Temp temp = null;
        String tempCtx = "unknown";
        if (!temps.isEmpty()) {
            double tavg = Py.mean(temps);
            temp = new Temp(temps.stream().min(Integer::compare).orElseThrow(), Py.round(tavg, 1),
                    temps.stream().max(Integer::compare).orElseThrow());
            tempCtx = tavg < s.temperature().coldBelow() ? "cold" : tavg > s.temperature().hotAbove() ? "hot" : "normal";
        }

        double ftp = s.athlete().ftp();
        Integer fitFtp = session.thresholdPower();
        if ("fit".equals(s.athlete().ftpSource()) && fitFtp != null && fitFtp != 0) {
            ftp = fitFtp;
        }
        AnalysisResult.Power power = hasPower ? power(ftp, fitFtp, totalSec) : null;

        List<Integer> mvIdx = IntStream.range(0, n).filter(i -> truthy(hr[i]) && spd[i] != null && spd[i] > 2.0).boxed().toList();
        Double ef = null;
        Double efPower = null;
        if (!mvIdx.isEmpty()) {
            double meanSpeed = Py.mean(mvIdx.stream().map(i -> spd[i]).toList());
            double meanHr = Py.mean(mvIdx.stream().map(i -> hr[i]).toList());
            ef = Py.round(meanSpeed * 60 / meanHr, 3);
        }
        if (hasPower && !hrs.isEmpty()) {
            efPower = hrAvg != null && hrAvg != 0 ? Py.round((double) power.np() / hrAvg, 3) : null;
        }

        Decoupling decoupling = new DecouplingCalculator(s, ts.length, hr, spd, altS, pwr, dts, indoor, hasPower, tempCtx).calculate();

        List<Effort> efforts = efforts();

        double vi = hasPower && power.avg() != 0 ? (double) power.np() / power.avg() : 1.0;
        if (decoupling.pct() != null && (vi > 1.06 || efforts.size() >= 3)) {
            decoupling = decoupling.withReliability(false, " Внимание: тренировка интервальная (VI " + Py.format(vi, 2, false)
                    + ", эффортов " + efforts.size() + ") — дрейф по всему заезду не показателен.");
        } else {
            decoupling = decoupling.withReliability(decoupling.pct() != null, "");
        }

        Series series = series(altS, hasPower, !temps.isEmpty());

        LocalDateTime startLocal = localTime(ts[0]);
        List<String> warnings = new ArrayList<>();
        if (hrArtifact) {
            warnings.add("Максимум пульса " + hrMax + " зафиксирован на скорости " + hrMaxSpeed
                    + " км/ч — похоже на артефакт ремня на спуске.");
        }
        if (!hasHr) {
            warnings.add("В файле нет данных пульса — зоны и EF не посчитаны.");
        }
        if (indoor && !hasPower) {
            warnings.add("Станок без мощности: скорость синтетическая, EF и дрейф ориентировочные.");
        }

        Meta meta = new Meta(filename, startLocal.format(MINUTES), startLocal.toLocalDate().toString(),
                session.sport() != null && !session.sport().isEmpty() ? session.sport() : "cycling",
                sub.isEmpty() ? null : sub, indoor, hasGps, hasPower, hasHr, device);
        Summary summary = new Summary(
                Py.round(totalDistM / 1000, 2), Py.round(totalSec / 60, 1), Py.round(movingSec / 60, 1), Py.round(stoppedSec / 60, 1),
                Py.round(avgSpeed, 1), Py.round(maxSpeed, 1), hrAvg, hrMax, hrMaxSpeed, cadAvg, cadMax, cad80, cad90,
                Py.round(ascent), Py.round(descent), session.totalAscent(), temp, tempCtx, session.totalCalories(), Py.round(load));
        AnalysisResult.HrZones hrZones = new AnalysisResult.HrZones(bounds, zones,
                new Z2BandShare(z2b.lo(), z2b.hi(), Py.round(z2bandSec / 60, 1), Py.round(100 * z2bandSec / hrSec, 1)),
                Py.round(100 * (zsec[0] + zsec[1]) / hrSec, 1));
        return new AnalysisResult(meta, summary, hrZones, ef, efPower, decoupling, efforts, halves, power, warnings, series,
                new SettingsUsed(bounds, ftp, List.of(z2b.lo(), z2b.hi())));
    }

    private AnalysisResult.Power power(double ftp, Integer fitFtp, double totalSec) {
        Double[] pF = Arrays.stream(pwr).map(p -> p != null ? p.doubleValue() : 0.0).toArray(Double[]::new);
        Double[] p30 = Py.rollingMean(pF, 30);
        double np = Math.pow(Py.mean(Arrays.stream(p30).filter(Objects::nonNull).map(x -> Math.pow(x, 4)).toList()), 0.25);
        List<Double> pNz = Arrays.stream(pF).filter(p -> p > 0).toList();
        double pavg = Py.mean(Arrays.asList(pF));
        double intensity = ftp != 0 ? np / ftp : 0;
        double tss = ftp != 0 ? (totalSec * np * intensity) / (ftp * 3600) * 100 : 0;
        List<Integer> pb = s.power().zonesPct().stream().map(p -> (int) Py.round(ftp * p)).toList();
        int nz = pb.size() + 1;
        double[] pz = new double[nz];
        for (int i = 0; i < n; i++) {
            double p = pF[i];
            pz[(int) pb.stream().filter(b -> p >= b).count()] += dts[i];
        }
        double psecSum = Py.sum(pz);
        double psec = psecSum == 0 ? 1 : psecSum;
        List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < nz; i++) {
            zones.add(new Zone(i < POWER_ZONE_NAMES.size() ? POWER_ZONE_NAMES.get(i) : "Z" + (i + 1), i > 0 ? pb.get(i - 1) : 0,
                    i < nz - 1 ? pb.get(i) : null, Py.round(pz[i] / 60, 1), Py.round(100 * pz[i] / psec, 1)));
        }
        double thr = s.power().ergBlockMinPctFtp() * ftp;
        List<int[]> spans = spans(i -> p30[i] != null && p30[i] >= thr, s.power().ergBlockMinSec());
        List<ErgBlock> blocks = new ArrayList<>();
        for (int[] span : spans) {
            int from = span[0];
            int to = span[1];
            if (to - from > 60) {
                from += 15;
                to -= 15;
            }
            List<Double> segP = Arrays.asList(pF).subList(from, to + 1);
            List<Integer> segH = range(from, to).mapToObj(i -> hr[i]).filter(WorkoutAnalyzer::truthy).toList();
            List<Integer> segC = range(from, to).mapToObj(i -> cad[i]).filter(WorkoutAnalyzer::truthy).toList();
            double meanP = Py.mean(segP);
            double cv = meanP != 0 ? Py.pstdev(segP) / meanP * 100 : 0;
            int q = Math.max(1, segH.size() / 4);
            blocks.add(new ErgBlock(
                    Py.round(sumDts(0, from - 1) / 60, 1), Py.round(sumDts(from, to) / 60, 1), Py.round(meanP),
                    ftp != 0 ? Py.round(100 * meanP / ftp) : null, Py.round(cv, 1),
                    segC.isEmpty() ? null : Py.round(Py.mean(segC)),
                    segH.isEmpty() ? null : Py.round(Py.mean(segH.subList(0, q))),
                    segH.isEmpty() ? null : Py.round(Py.mean(segH.subList(segH.size() - q, segH.size()))),
                    segH.isEmpty() ? null : Py.round(Py.mean(segH))));
        }
        int max = (int) Arrays.stream(pF).mapToDouble(Double::doubleValue).max().orElse(0);
        return new AnalysisResult.Power(ftp, fitFtp, Py.round(pavg), pNz.isEmpty() ? 0 : Py.round(Py.mean(pNz)), max,
                Py.round(np), Py.round(intensity, 2), Py.round(tss), Py.round(ftp / s.athlete().weightKg(), 2), zones, blocks);
    }

    private List<Effort> efforts() {
        double threshold = a.effortHrThreshold();
        List<Effort> out = new ArrayList<>();
        for (int[] span : spans(i -> hr[i] != null && hr[i] >= threshold, a.effortMinSec())) {
            List<Integer> seg = range(span[0], span[1]).mapToObj(i -> hr[i]).filter(WorkoutAnalyzer::truthy).toList();
            List<Integer> segP = range(span[0], span[1]).mapToObj(i -> pwr[i]).filter(WorkoutAnalyzer::truthy).toList();
            out.add(new Effort(Py.round(sumDts(0, span[0] - 1) / 60, 1), Py.round(sumDts(span[0], span[1]) / 60, 1),
                    Py.round(Py.mean(seg)), seg.stream().max(Integer::compare).orElseThrow(),
                    segP.isEmpty() ? null : Py.round(Py.mean(segP))));
        }
        return out;
    }

    private List<int[]> spans(java.util.function.IntPredicate inside, double minSec) {
        List<int[]> spans = new ArrayList<>();
        int[] cur = null;
        for (int i = 0; i < n; i++) {
            if (inside.test(i)) {
                cur = cur == null ? new int[] {i, i} : new int[] {cur[0], i};
            } else {
                if (cur != null && sumDts(cur[0], cur[1]) >= minSec) {
                    spans.add(cur);
                }
                cur = null;
            }
        }
        if (cur != null && sumDts(cur[0], cur[1]) >= minSec) {
            spans.add(cur);
        }
        return spans;
    }

    private Series series(Double[] altS, boolean hasPower, boolean hasTemps) {
        int step = Math.max(1, (int) Math.ceil((double) n / a.seriesMaxPoints()));
        Double[] spdKmh = Arrays.stream(spd).map(v -> v != null ? v * 3.6 : null).toArray(Double[]::new);
        Double[] spdR = Py.rollingMean(spdKmh, a.speedRolling());
        double[] tMin = new double[n];
        double acc = 0.0;
        for (int i = 0; i < n; i++) {
            acc += dts[i];
            tMin[i] = acc / 60;
        }
        int[] idx = IntStream.iterate(0, i -> i < n, i -> i + step).toArray();
        return new Series(
                pick(idx, i -> Py.round(tMin[i], 2)),
                pick(idx, i -> Py.round((truthy(dist[i]) ? dist[i] : 0) / 1000, 3)),
                pick(idx, i -> Py.round(altS[i], 1)),
                pick(idx, i -> hr[i]),
                pick(idx, i -> spdR[i] != null ? Py.round(spdR[i], 1) : null),
                pick(idx, i -> cad[i]),
                hasPower ? pick(idx, i -> pwr[i]) : null,
                hasTemps ? pick(idx, i -> tmp[i]) : null,
                pick(idx, i -> lat[i] != null ? lat[i] * SEMICIRCLE_DEGREES : null),
                pick(idx, i -> lon[i] != null ? lon[i] * SEMICIRCLE_DEGREES : null));
    }

    private LocalDateTime localTime(long epochSeconds) {
        LocalDateTime utc = LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC);
        return s.time().fitIsLocal() ? utc : utc.plusNanos(Math.round(s.time().offsetHours() * 3_600_000_000_000.0));
    }

    private AltitudeHalf altitudeHalf(Double[] altS, int from, int to) {
        double up = climb(altS, from + 1, to, true);
        double down = climb(altS, from + 1, to, false);
        int last = Math.min(to, n) - 1;
        double end = altS[last < 0 ? n + last : last];
        return new AltitudeHalf(Py.round(up), Py.round(down), Py.round(end - altS[from]));
    }

    private static double climb(Double[] altS, int from, int to, boolean up) {
        return Py.sum(IntStream.range(from, to)
                .mapToDouble(i -> Math.max(0.0, up ? altS[i] - altS[i - 1] : altS[i - 1] - altS[i])).toArray());
    }

    private double sumDts(int from, int to) {
        return to < from ? 0.0 : Py.sum(Arrays.copyOfRange(dts, from, to + 1));
    }

    private static IntStream range(int from, int to) {
        return IntStream.rangeClosed(from, to);
    }

    private static <T> List<T> pick(int[] idx, IntFunction<T> value) {
        List<T> out = new ArrayList<>(idx.length);
        for (int i : idx) {
            out.add(value.apply(i));
        }
        return out;
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    static boolean truthy(Number v) {
        return v != null && v.doubleValue() != 0;
    }
}
