package ru.lopon.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

public final class Py {

    private static final MathContext PRECISION = new MathContext(60);

    private Py() {
    }

    public static double sum(Collection<? extends Number> values) {
        return sum(values.stream().mapToDouble(Number::doubleValue).toArray());
    }

    public static double sum(double[] values) {
        double total = 0.0;
        double compensation = 0.0;
        for (double x : values) {
            double t = total + x;
            if (Math.abs(total) >= Math.abs(x)) {
                compensation += (total - t) + x;
            } else {
                compensation += (x - t) + total;
            }
            total = t;
        }
        return compensation != 0.0 && Double.isFinite(compensation) ? total + compensation : total;
    }

    public static double mean(Collection<? extends Number> values) {
        return mean(values.stream().mapToDouble(Number::doubleValue).toArray());
    }

    public static double mean(double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("mean requires at least one data point");
        }
        return exactSum(values).divide(BigDecimal.valueOf(values.length), PRECISION).doubleValue();
    }

    public static double pstdev(Collection<? extends Number> values) {
        double[] xs = values.stream().mapToDouble(Number::doubleValue).toArray();
        BigDecimal n = BigDecimal.valueOf(xs.length);
        BigDecimal sum = exactSum(xs);
        BigDecimal squares = BigDecimal.ZERO;
        for (double x : xs) {
            BigDecimal b = new BigDecimal(x);
            squares = squares.add(b.multiply(b));
        }
        BigDecimal numerator = n.multiply(squares).subtract(sum.multiply(sum));
        return numerator.sqrt(PRECISION).divide(n, PRECISION).doubleValue();
    }

    public static double round(double x, int digits) {
        if (!Double.isFinite(x)) {
            return x;
        }
        double rounded = new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
        return rounded == 0.0 ? Math.copySign(0.0, x) : rounded;
    }

    public static long round(double x) {
        return (long) Math.rint(x);
    }

    public static String format(double x, int digits, boolean plusSign) {
        boolean negative = Math.copySign(1.0, x) < 0;
        String abs = new BigDecimal(Math.abs(x)).setScale(digits, RoundingMode.HALF_EVEN).toPlainString();
        return (negative ? "-" : plusSign ? "+" : "") + abs;
    }

    public static String str(double x) {
        return x == Math.rint(x) && Math.abs(x) < 1e15 ? Long.toString((long) x) : Double.toString(x);
    }

    public static Double[] rollingMean(Double[] values, int window) {
        Double[] out = new Double[values.length];
        Deque<Double> buffer = new ArrayDeque<>();
        double s = 0.0;
        for (int i = 0; i < values.length; i++) {
            Double v = values[i];
            if (v == null) {
                continue;
            }
            buffer.addLast(v);
            s += v;
            if (buffer.size() > window) {
                s -= buffer.removeFirst();
            }
            out[i] = s / buffer.size();
        }
        return out;
    }

    public static Double[] forwardFill(Double[] values, double fallback) {
        Double[] out = new Double[values.length];
        Double last = null;
        for (int i = 0; i < values.length; i++) {
            Double v = values[i] != null ? values[i] : last != null ? last : fallback;
            out[i] = v;
            last = v;
        }
        return out;
    }

    private static BigDecimal exactSum(double[] values) {
        BigDecimal total = BigDecimal.ZERO;
        for (double x : values) {
            total = total.add(new BigDecimal(x));
        }
        return total;
    }
}
