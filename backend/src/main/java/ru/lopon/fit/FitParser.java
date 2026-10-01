package ru.lopon.fit;

import com.garmin.fit.DateTime;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.FitDecoder;
import com.garmin.fit.FitMessages;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import com.garmin.fit.SubSport;
import java.io.ByteArrayInputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class FitParser {

    private static final long FIT_EPOCH_OFFSET_SECONDS = DateTime.OFFSET / 1000;

    private FitParser() {
    }

    public static ParsedFit parse(byte[] data) {
        FitMessages messages;
        try {
            messages = new FitDecoder().decode(new ByteArrayInputStream(data));
        } catch (RuntimeException e) {
            throw new FitParseException("Файл не читается как FIT. Проверь, что это запись велокомпьютера, а не GPX.", e);
        }
        List<FitRecord> records = messages.getRecordMesgs().stream()
                .filter(r -> r.getTimestamp() != null)
                .map(FitParser::record)
                .sorted(Comparator.comparingLong(FitRecord::timestamp))
                .toList();
        return new ParsedFit(session(messages.getSessionMesgs()), manufacturer(messages.getFileIdMesgs()), records);
    }

    private static FitRecord record(RecordMesg r) {
        return new FitRecord(
                r.getTimestamp().getTimestamp() + FIT_EPOCH_OFFSET_SECONDS,
                r.getFieldIntegerValue(RecordMesg.HeartRateFieldNum),
                either(r.getFieldDoubleValue(RecordMesg.EnhancedSpeedFieldNum), r.getFieldDoubleValue(RecordMesg.SpeedFieldNum)),
                either(r.getFieldDoubleValue(RecordMesg.EnhancedAltitudeFieldNum), r.getFieldDoubleValue(RecordMesg.AltitudeFieldNum)),
                r.getFieldIntegerValue(RecordMesg.CadenceFieldNum),
                r.getFieldIntegerValue(RecordMesg.PowerFieldNum),
                r.getFieldIntegerValue(RecordMesg.TemperatureFieldNum),
                r.getFieldDoubleValue(RecordMesg.DistanceFieldNum),
                r.getFieldIntegerValue(RecordMesg.PositionLatFieldNum),
                r.getFieldIntegerValue(RecordMesg.PositionLongFieldNum));
    }

    private static FitSession session(List<SessionMesg> sessions) {
        return new FitSession(
                last(sessions, SessionMesg.SportFieldNum, s -> name(s.getFieldIntegerValue(SessionMesg.SportFieldNum), v -> Sport.getByValue(v.shortValue()), Sport.INVALID)),
                last(sessions, SessionMesg.SubSportFieldNum, s -> name(s.getFieldIntegerValue(SessionMesg.SubSportFieldNum), v -> SubSport.getByValue(v.shortValue()), SubSport.INVALID)),
                last(sessions, SessionMesg.TotalDistanceFieldNum, s -> s.getFieldDoubleValue(SessionMesg.TotalDistanceFieldNum)),
                last(sessions, SessionMesg.ThresholdPowerFieldNum, s -> s.getFieldIntegerValue(SessionMesg.ThresholdPowerFieldNum)),
                last(sessions, SessionMesg.TotalAscentFieldNum, s -> s.getFieldIntegerValue(SessionMesg.TotalAscentFieldNum)),
                last(sessions, SessionMesg.TotalCaloriesFieldNum, s -> s.getFieldIntegerValue(SessionMesg.TotalCaloriesFieldNum)));
    }

    private static <T> T last(List<SessionMesg> sessions, int field, Function<SessionMesg, T> value) {
        T result = null;
        for (SessionMesg s : sessions) {
            if (s.hasField(field)) {
                result = value.apply(s);
            }
        }
        return result;
    }

    private static <E extends Enum<E>> String name(Integer raw, Function<Integer, E> lookup, E invalid) {
        if (raw == null) {
            return null;
        }
        E value = lookup.apply(raw);
        return value == invalid ? raw.toString() : value.name().toLowerCase(Locale.ROOT);
    }

    private static String manufacturer(List<FileIdMesg> fileIds) {
        return fileIds.stream()
                .map(FileIdMesg::getManufacturer)
                .filter(Objects::nonNull)
                .map(Manufacturer::getStringFromValue)
                .filter(Objects::nonNull)
                .findFirst()
                .map(m -> m.toLowerCase(Locale.ROOT))
                .orElse("");
    }

    private static Double either(Double primary, Double fallback) {
        return primary != null ? primary : fallback;
    }
}
