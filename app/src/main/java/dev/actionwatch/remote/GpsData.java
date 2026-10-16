// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Independent encoder for the documented DJI R SDK 00/17 GPS data segment.
 * No vendor implementation is included. Values are measured values in SI units;
 * this class never creates a location, fills in missing measurements, or changes
 * a fix's original timestamp when it is sent again.
 */
public final class GpsData {
    public static final int PAYLOAD_BYTES = 48;
    public static final long MAX_FIX_AGE_MS = 2_000;
    public static final long TRANSPORT_INTERVAL_MS = 100;
    private static final long UINT32_MAX = 0xffff_ffffL;
    private static final ZoneOffset CAMERA_OFFSET = ZoneOffset.ofHours(8);
    // Last millisecond that still has a four-digit year in UTC+08:00.
    private static final long MAX_UTC_MILLIS = 253_402_271_999_999L;

    private GpsData() {}

    /**
     * One complete fix. All fields are immutable, and all timestamps belong to
     * the actual measurement, not the callback or transport time.
     *
     * The documented wire field is simply "height". DJI's reference GPS source
     * uses GGA altitude, so this app explicitly uses mean-sea-level (MSL) height.
     * Android Location.getAltitude() is ellipsoidal and is not a substitute.
     * All accuracy fields must come from the provider. There is no documented
     * unknown-field sentinel, and NaN, infinity and negative accuracy are rejected.
     *
     * The controller must reject mock locations in real mode, check provider
     * presence flags, match satellite data to the fix, and supply measured or
     * explicitly documented sensor-derived velocity. Android Location has no
     * vertical-velocity field. A slope from two real same-datum altitudes is an
     * estimate; its first sample cannot be silently replaced with zero.
     */
    public static final class Fix {
        public final long utcMillis;
        public final long elapsedRealtimeMs;
        public final double longitudeDegrees;
        public final double latitudeDegrees;
        public final double altitudeMslMeters;
        public final double northMps;
        public final double eastMps;
        public final double downMps;
        public final double verticalAccuracyMeters;
        public final double horizontalAccuracyMeters;
        public final double speedAccuracyMps;
        public final long satellites;

        public Fix(long utcMillis, long elapsedRealtimeMs,
                   double longitudeDegrees, double latitudeDegrees,
                   double altitudeMslMeters, double northMps, double eastMps,
                   double downMps, double verticalAccuracyMeters,
                   double horizontalAccuracyMeters, double speedAccuracyMps,
                   long satellites) {
            this.utcMillis = utcMillis;
            this.elapsedRealtimeMs = elapsedRealtimeMs;
            this.longitudeDegrees = longitudeDegrees;
            this.latitudeDegrees = latitudeDegrees;
            this.altitudeMslMeters = altitudeMslMeters;
            this.northMps = northMps;
            this.eastMps = eastMps;
            this.downMps = downMps;
            this.verticalAccuracyMeters = verticalAccuracyMeters;
            this.horizontalAccuracyMeters = horizontalAccuracyMeters;
            this.speedAccuracyMps = speedAccuracyMps;
            this.satellites = satellites;
        }
    }

    /** Returns null only for a complete, representable fix no more than 2 s old. */
    public static String invalidReason(Fix fix, long nowElapsedMs) {
        if (fix == null) return "No GPS fix";
        if (nowElapsedMs < 0 || fix.elapsedRealtimeMs < 0)
            return "Invalid monotonic timestamp";
        if (fix.elapsedRealtimeMs > nowElapsedMs) return "GPS fix is in the future";
        if (nowElapsedMs - fix.elapsedRealtimeMs > MAX_FIX_AGE_MS)
            return "GPS fix is stale";
        if (fix.utcMillis <= 0 || fix.utcMillis > MAX_UTC_MILLIS)
            return "Missing or unsupported UTC timestamp";
        if (!inRange(fix.longitudeDegrees, -180, 180))
            return "Invalid longitude";
        if (!inRange(fix.latitudeDegrees, -90, 90)) return "Invalid latitude";
        if (!inRange(fix.altitudeMslMeters * 1000, Integer.MIN_VALUE, Integer.MAX_VALUE))
            return "Missing or unrepresentable MSL altitude";
        if (!velocityFits(fix.northMps) || !velocityFits(fix.eastMps)
                || !velocityFits(fix.downMps)) return "Missing or unrepresentable velocity";
        if (!accuracyFits(fix.verticalAccuracyMeters, 1000))
            return "Missing or unrepresentable vertical accuracy";
        if (!accuracyFits(fix.horizontalAccuracyMeters, 1000))
            return "Missing or unrepresentable horizontal accuracy";
        if (!accuracyFits(fix.speedAccuracyMps, 100))
            return "Missing or unrepresentable speed accuracy";
        if (fix.satellites < 0 || fix.satellites > UINT32_MAX)
            return "Missing or unrepresentable satellite count";
        return null;
    }

    /**
     * Encodes only the 48-byte payload; wrap with Protocol.frame(0, 0x17, ...).
     * UTC+8 is applied to the complete calendar date as well as time, including
     * month, leap-day and year rollover. Integers and IEEE-754 floats are LE.
     * Coordinates and height round to the nearest integer unit. Accuracy rounds
     * upward, so conversion never advertises better precision than the source.
     *
     * @throws IllegalArgumentException if the fix is incomplete, stale or invalid
     */
    public static byte[] encode(Fix fix, long nowElapsedMs) {
        String problem = invalidReason(fix, nowElapsedMs);
        if (problem != null) throw new IllegalArgumentException(problem);
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(fix.utcMillis), CAMERA_OFFSET);
        byte[] payload = new byte[PAYLOAD_BYTES];
        put32(payload, 0, time.getYear() * 10_000 + time.getMonthValue() * 100 + time.getDayOfMonth());
        put32(payload, 4, time.getHour() * 10_000 + time.getMinute() * 100 + time.getSecond());
        put32(payload, 8, Math.round(fix.longitudeDegrees * 10_000_000));
        put32(payload, 12, Math.round(fix.latitudeDegrees * 10_000_000));
        put32(payload, 16, Math.round(fix.altitudeMslMeters * 1000));
        putFloat(payload, 20, fix.northMps * 100);
        putFloat(payload, 24, fix.eastMps * 100);
        putFloat(payload, 28, fix.downMps * 100);
        put32(payload, 32, (long) Math.ceil(fix.verticalAccuracyMeters * 1000));
        put32(payload, 36, (long) Math.ceil(fix.horizontalAccuracyMeters * 1000));
        put32(payload, 40, (long) Math.ceil(fix.speedAccuracyMps * 100));
        put32(payload, 44, fix.satellites);
        return payload;
    }

    private static boolean inRange(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    private static boolean velocityFits(double metersPerSecond) {
        // Do not clamp an out-of-range sensor value to a fabricated speed.
        double cmPerSecond = metersPerSecond * 100;
        return Double.isFinite(cmPerSecond)
                && cmPerSecond >= -Float.MAX_VALUE && cmPerSecond <= Float.MAX_VALUE;
    }

    private static boolean accuracyFits(double value, int scale) {
        return inRange(value * scale, 0, UINT32_MAX);
    }

    private static void put32(byte[] payload, int offset, long value) {
        Protocol.put(payload, offset, value, 4);
    }

    private static void putFloat(byte[] payload, int offset, double value) {
        put32(payload, offset, Float.floatToRawIntBits((float) value));
    }

    /** A checksum-verified GNSS GGA measurement; it is not a full dated fix. */
    public static final class Gga {
        public final double altitudeMslMeters;
        public final int satellites;
        public final int secondsOfDay;
        public final int millisOfDay;
        public final double latitudeDegrees;
        public final double longitudeDegrees;

        private Gga(double altitudeMslMeters, int satellites, int millisOfDay,
                    double latitudeDegrees, double longitudeDegrees) {
            this.altitudeMslMeters = altitudeMslMeters;
            this.satellites = satellites;
            this.millisOfDay = millisOfDay;
            this.secondsOfDay = millisOfDay / 1000;
            this.latitudeDegrees = latitudeDegrees;
            this.longitudeDegrees = longitudeDegrees;
        }
    }

    /**
     * Parses one GPGGA/GNGGA sentence with a mandatory XOR checksum. Invalid,
     * estimated/dead-reckoning, manual and simulated fixes are rejected. Empty
     * fields are preserved, so a missing altitude never shifts another field
     * into its position. GGA altitude is MSL in meters; HDOP is not converted to
     * an invented accuracy estimate.
     *
     * GGA contains only a UTC time of day, not a date or monotonic age. The
     * controller must pair this with a real, non-mock Android GNSS fix using
     * its UTC timestamp, and also bound NMEA callback age on the monotonic clock.
     */
    public static Gga parseGga(String sentence) {
        if (sentence == null || sentence.length() > 256) return null;
        String line = sentence.trim();
        if (!line.startsWith("$") || line.length() < 10) return null;
        int star = line.indexOf('*');
        if (star < 1 || star != line.length() - 3) return null;
        int high = Character.digit(line.charAt(star + 1), 16);
        int low = Character.digit(line.charAt(star + 2), 16);
        if (high < 0 || low < 0 || line.charAt(star + 1) > 127
                || line.charAt(star + 2) > 127) return null;
        int checksum = 0;
        for (int i = 1; i < star; i++) {
            char c = line.charAt(i);
            if (c < 32 || c > 126) return null;
            checksum ^= c;
        }
        if (checksum != ((high << 4) | low)) return null;
        String[] fields = line.substring(1, star).split(",", -1);
        if (fields.length != 15
                || !("GPGGA".equals(fields[0]) || "GNGGA".equals(fields[0]))) return null;
        try {
            if (!fields[6].matches("[1-5]") || !fields[7].matches("[0-9]{1,3}")) return null;
            int satellites = Integer.parseInt(fields[7]);
            if (satellites == 0 || !"M".equals(fields[10])) return null;
            int millisOfDay = parseUtcMillisOfDay(fields[1]);
            double latitude = parseNmeaCoordinate(fields[2], fields[3], true);
            double longitude = parseNmeaCoordinate(fields[4], fields[5], false);
            if (!fields[9].matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) return null;
            double altitude = Double.parseDouble(fields[9]);
            if (!inRange(altitude * 1000, Integer.MIN_VALUE, Integer.MAX_VALUE)) return null;
            return new Gga(altitude, satellites, millisOfDay, latitude, longitude);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static int parseUtcMillisOfDay(String text) {
        if (!text.matches("[0-9]{6}(?:\\.[0-9]{1,9})?"))
            throw new IllegalArgumentException("Invalid GGA UTC time");
        int hours = Integer.parseInt(text.substring(0, 2));
        int minutes = Integer.parseInt(text.substring(2, 4));
        int seconds = Integer.parseInt(text.substring(4, 6));
        if (hours > 23 || minutes > 59 || seconds > 59)
            throw new IllegalArgumentException("Invalid GGA UTC time");
        int millis = 0;
        // Truncate sub-millisecond precision without floating-point rollover.
        if (text.length() > 6) {
            String fraction = (text.substring(7) + "000").substring(0, 3);
            millis = Integer.parseInt(fraction);
        }
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis;
    }

    private static double parseNmeaCoordinate(String text, String hemisphere, boolean latitude) {
        int degreeDigits = latitude ? 2 : 3;
        if (!text.matches("[0-9]{" + (degreeDigits + 2) + "}(?:\\.[0-9]+)?"))
            throw new IllegalArgumentException("Invalid GGA coordinate");
        int degrees = Integer.parseInt(text.substring(0, degreeDigits));
        double minutes = Double.parseDouble(text.substring(degreeDigits));
        int limit = latitude ? 90 : 180;
        if (minutes >= 60 || degrees > limit || (degrees == limit && minutes != 0))
            throw new IllegalArgumentException("Invalid GGA coordinate");
        String positive = latitude ? "N" : "E";
        String negative = latitude ? "S" : "W";
        if (!positive.equals(hemisphere) && !negative.equals(hemisphere))
            throw new IllegalArgumentException("Invalid GGA hemisphere");
        double value = degrees + minutes / 60;
        return negative.equals(hemisphere) ? -value : value;
    }

    /**
     * A transport-rate gate, not a GPS sensor sampler. Poll with the newest fix.
     * Repeated sends retain that fix's original data and timestamps and stop as
     * soon as its monotonic age exceeds 2 s. Each call handles one possible slot;
     * there is no catch-up loop, queue, interpolation or extrapolation.
     *
     * A busy/disconnected link or invalid fix drops the due slot. The caller must
     * atomically check link readiness and send the returned payload immediately;
     * if the link becomes busy meanwhile, discard the payload rather than queue
     * it. Confine poll/reset and the link check to the same controller thread.
     */
    public static final class Gate {
        private long lastSlotMs = -1;

        public byte[] poll(Fix latest, long nowElapsedMs,
                           boolean protocolConnected, boolean linkReady) {
            if (nowElapsedMs < 0) return null;
            if (lastSlotMs >= 0 && nowElapsedMs < lastSlotMs) {
                // Monotonic clocks do not move backward during a boot. A caller
                // crossing boot epochs must clear its fix and reset this gate.
                return null;
            }
            if (lastSlotMs >= 0 && nowElapsedMs - lastSlotMs < TRANSPORT_INTERVAL_MS)
                return null;
            lastSlotMs = nowElapsedMs;
            if (!protocolConnected || !linkReady || invalidReason(latest, nowElapsedMs) != null)
                return null;
            return encode(latest, nowElapsedMs);
        }

        /** Call when stopping GPS or starting a new connection/clock epoch. */
        public void reset() {
            lastSlotMs = -1;
        }
    }
}
