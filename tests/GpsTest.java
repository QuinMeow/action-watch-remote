// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;

import java.time.Instant;
import java.util.Arrays;
import java.util.Random;

/** JVM-only checks; fixtures are test data and are never used by real GPS mode. */
public final class GpsTest {
    private static int count;
    private static final long UTC = Instant.parse("2024-02-29T16:00:01Z").toEpochMilli();
    private static final double[] VALUES = {
        -122.1234567, 37.7654321, -1.25, 12.5, -4.75, -.3, 1.234, 5.678, .12
    };

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        count++;
        System.out.println("PASS " + label);
    }

    private static GpsData.Fix fix(long utc, long elapsed, double[] value, long satellites) {
        return new GpsData.Fix(utc, elapsed, value[0], value[1], value[2], value[3],
                value[4], value[5], value[6], value[7], value[8], satellites);
    }

    private static GpsData.Fix fix() { return fix(UTC, 1000, VALUES, 14); }

    private static GpsData.Fix changed(int field, double value) {
        double[] copy = VALUES.clone();
        copy[field] = value;
        return fix(UTC, 1000, copy, 14);
    }

    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    private static int signed(byte[] bytes, int offset) { return (int) Protocol.u32(bytes, offset); }
    private static float floating(byte[] bytes, int offset) {
        return Float.intBitsToFloat(signed(bytes, offset));
    }

    private static void rejected(GpsData.Fix fix, long now, String label) {
        boolean threw = false;
        try { GpsData.encode(fix, now); } catch (IllegalArgumentException expected) { threw = true; }
        check(threw && GpsData.invalidReason(fix, now) != null, label);
    }

    private static void calendar(String utc, int date, int time, String label) {
        byte[] payload = GpsData.encode(fix(Instant.parse(utc).toEpochMilli(), 1000, VALUES, 14), 1000);
        check(signed(payload, 0) == date && signed(payload, 4) == time, label);
    }

    private static String sentence(String body) {
        int checksum = 0;
        for (char c : body.toCharArray()) checksum ^= c;
        return "$" + body + "*" + String.format(java.util.Locale.ROOT, "%02X", checksum);
    }

    private static String gga(String utc, String quality, String satellites, String altitude, String unit) {
        return sentence("GNGGA," + utc + ",4807.038,N,01131.000,E," + quality + ","
                + satellites + ",0.9," + altitude + "," + unit + ",46.9,M,,");
    }

    public static void main(String[] args) {
        byte[] payload = GpsData.encode(fix(), 1000);
        check(payload.length == 48, "GPS payload is exactly 48 bytes");
        // Independent <iiiii fff IIII little-endian fixture for the measurements above.
        check(Arrays.equals(payload, hex("add7340101000000797035b7318c82161efbffff00409c440080edc30000f0c1d20400002e1600000c0000000e000000")),
                "48-byte fixture matches every field and little-endian offset");
        check(signed(payload, 8) == -1221234567 && signed(payload, 12) == 377654321,
                "longitude precedes latitude with signed 1e7 scaling");
        check(signed(payload, 16) == -1250, "negative MSL altitude remains signed millimeters");
        check(floating(payload, 20) == 1250 && floating(payload, 24) == -475
                && floating(payload, 28) == -30, "north east down velocities are float32 cm per second");
        check(Protocol.u32(payload, 32) == 1234 && Protocol.u32(payload, 36) == 5678
                && Protocol.u32(payload, 40) == 12 && Protocol.u32(payload, 44) == 14,
                "provider accuracy and satellite fields have correct units and offsets");
        byte[] frame = Protocol.frame(0, 0x17, 0, 7, payload);
        Protocol.Frame decoded = new Protocol.Decoder().feed(frame, 1000).get(0);
        check(frame.length == 66 && decoded.set == 0 && decoded.id == 0x17
                && Arrays.equals(decoded.payload, payload), "GPS payload wraps in CRC-valid command 0017");

        calendar("2024-12-31T16:00:00Z", 20250101, 0, "UTC+8 rolls calendar into next year");
        calendar("2024-02-28T16:00:00Z", 20240229, 0, "UTC+8 leap-day rollover");
        calendar("2023-02-28T16:00:00Z", 20230301, 0, "UTC+8 non-leap month rollover");
        calendar("2024-03-31T15:59:59.999Z", 20240331, 235959, "subsecond timestamp truncates without next-day drift");
        calendar("2024-03-31T16:00:00Z", 20240401, 0, "UTC+8 midnight stays in 00 through 23 hour range");
        calendar("9999-12-31T15:59:59.999Z", 99991231, 235959, "largest supported UTC+8 date is representable");
        rejected(fix(253402272000000L, 1000, VALUES, 14), 1000, "reject date outside four-digit UTC+8 year");
        rejected(fix(0, 1000, VALUES, 14), 1000, "reject missing UTC time");
        check(GpsData.invalidReason(fix(), 3000) == null, "fix is fresh at inclusive 2000 ms boundary");
        rejected(fix(), 3001, "reject fix stale by one millisecond");
        rejected(fix(), 999, "reject future monotonic measurement time");
        rejected(fix(), -1, "reject negative current monotonic time");
        rejected(fix(UTC, -1, VALUES, 14), 1000, "reject missing measurement elapsed time");
        rejected(null, 1000, "reject absent location rather than create coordinates");
        rejected(fix(UTC, 0, VALUES, 14), Long.MAX_VALUE, "large elapsed age cannot overflow to fresh");
        rejected(changed(0, 180.0000001), 1000, "reject longitude over 180");
        rejected(changed(1, -90.0000001), 1000, "reject latitude below minus 90");
        check(signed(GpsData.encode(changed(0, -180), 1000), 8) == -1800000000,
                "accept negative longitude endpoint without overflow");
        check(signed(GpsData.encode(changed(1, 90), 1000), 12) == 900000000,
                "accept positive latitude endpoint without overflow");
        for (int field = 0; field < VALUES.length; field++) {
            rejected(changed(field, Double.NaN), 1000, "reject missing NaN field " + field);
            rejected(changed(field, Double.POSITIVE_INFINITY), 1000, "reject nonfinite field " + field);
        }
        rejected(changed(2, Integer.MAX_VALUE / 1000.0 + .001), 1000, "reject altitude integer overflow");
        rejected(changed(3, Double.MAX_VALUE), 1000, "reject velocity float overflow");
        for (int field = 6; field <= 8; field++)
            rejected(changed(field, -.001), 1000, "reject negative accuracy field " + field);
        rejected(changed(6, 4294967.296), 1000, "reject accuracy uint32 overflow");
        check(Protocol.u32(GpsData.encode(changed(6, 4294967.295), 1000), 32) == 0xffffffffL,
                "accuracy retains complete unsigned 32-bit range");
        check(Protocol.u32(GpsData.encode(changed(6, .0001), 1000), 32) == 1,
                "accuracy rounds conservatively upward instead of claiming zero uncertainty");
        rejected(fix(UTC, 1000, VALUES, -1), 1000, "reject missing satellite count");
        rejected(fix(UTC, 1000, VALUES, 0x100000000L), 1000, "reject satellite uint32 overflow");

        GpsData.Gate gate = new GpsData.Gate();
        check(gate.poll(fix(), 1000, false, true) == null, "disconnected camera drops GPS slot");
        check(gate.poll(fix(), 1100, true, false) == null, "busy link drops GPS slot");
        check(gate.poll(fix(), 1101, true, true) == null, "busy slot is not retained for immediate replay");
        check(Arrays.equals(gate.poll(fix(), 1200, true, true), payload), "fresh fix can be repeated in next slot");
        check(gate.poll(fix(), 1299, true, true) == null, "transport blocks intervals below 100 ms");
        check(Arrays.equals(gate.poll(fix(), 1300, true, true), payload), "repeated fix retains original timestamp and values");
        check(gate.poll(fix(), 1000, true, true) == null, "clock rollback cannot open a burst slot");
        gate.reset();
        int sent = 0;
        for (long now = 1000; now < 2000; now++) if (gate.poll(fix(), now, true, true) != null) sent++;
        check(sent == 10, "1000 polls produce at most ten transport packets per second");
        check(gate.poll(fix(), 2900, true, true) != null && gate.poll(fix(), 2901, true, true) == null,
                "delayed scheduler sends one packet without catch-up burst");
        check(gate.poll(fix(), 3000, true, true) != null && gate.poll(fix(), 3100, true, true) == null,
                "transport stops repeating as soon as fix is stale");
        gate.reset();
        check(gate.poll(null, 1000, true, true) == null, "transport does not send without a fix");
        check(gate.poll(changed(5, Double.NaN), 1100, true, true) == null,
                "missing downward velocity cannot become a fabricated zero");
        byte[] newer = gate.poll(changed(0, 0), 1200, true, true);
        check(newer != null && signed(newer, 8) == 0, "newest supplied fix replaces old data with no backlog");
        check(gate.poll(fix(), -1, true, true) == null, "negative scheduler clock cannot send");

        GpsData.Gga canonical = GpsData.parseGga("$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47");
        check(canonical != null && canonical.altitudeMslMeters == 545.4 && canonical.satellites == 8
                && canonical.secondsOfDay == 45319, "fixed GPGGA checksum fixture extracts actual MSL altitude and satellites");
        check(Math.abs(canonical.latitudeDegrees - 48.1173) < 1e-9
                && Math.abs(canonical.longitudeDegrees - 11.5166666667) < 1e-9,
                "GGA degree-minute coordinates retain real position");
        GpsData.Gga fractional = GpsData.parseGga(gga("235959.9999", "1", "12", "-12.34", "M") + "\r\n");
        check(fractional != null && fractional.millisOfDay == 86399999
                && fractional.altitudeMslMeters == -12.34, "GNGGA fractional time and below-sea-level altitude accepted");
        check(GpsData.parseGga(gga("000000", "1", "12", "0", "M")).millisOfDay == 0,
                "GGA midnight has unambiguous zero time of day");
        for (String quality : new String[]{"0", "6", "7", "8", "9", "", "-1"})
            check(GpsData.parseGga(gga("123519", quality, "08", "545.4", "M")) == null,
                    "reject non-GNSS GGA fix quality " + quality);
        for (String quality : new String[]{"1", "2", "3", "4", "5"})
            check(GpsData.parseGga(gga("123519", quality, "08", "545.4", "M")) != null,
                    "accept GNSS GGA fix quality " + quality);
        String valid = gga("123519.1", "1", "08", "545.4", "M");
        check(GpsData.parseGga(valid.substring(0, valid.length() - 1) + "Z") == null,
                "reject malformed hexadecimal checksum");
        check(GpsData.parseGga(valid.replace("545.4", "545.5")) == null,
                "reject checksum mismatch on modified measurement");
        check(GpsData.parseGga(valid.substring(0, valid.indexOf('*'))) == null,
                "GGA checksum is mandatory");
        check(GpsData.parseGga(gga("123519", "1", "08", "", "M")) == null,
                "empty GGA altitude stays missing rather than shifting fields");
        for (String altitude : new String[]{"NaN", "Infinity", "1e3", "2147483.648"})
            check(GpsData.parseGga(gga("123519", "1", "08", altitude, "M")) == null,
                    "reject invalid GGA altitude " + altitude);
        check(GpsData.parseGga(gga("123519", "1", "08", "545.4", "F")) == null,
                "reject non-meter GGA altitude");
        check(GpsData.parseGga(gga("123519", "1", "00", "545.4", "M")) == null,
                "positive GGA fix quality requires actual satellites");
        for (String time : new String[]{"240000", "126000", "125960", "12351", "123519.", "NaN"})
            check(GpsData.parseGga(gga(time, "1", "08", "545.4", "M")) == null,
                    "reject malformed GGA time " + time);
        check(GpsData.parseGga(sentence("GNGGA,123519,4860.001,N,01131.000,E,1,08,0.9,1,M,1,M,,")) == null,
                "reject GGA latitude minutes above 59");
        check(GpsData.parseGga(sentence("GNGGA,123519,9000.001,N,01131.000,E,1,08,0.9,1,M,1,M,,")) == null,
                "reject GGA latitude beyond pole");
        check(GpsData.parseGga(sentence("GNGGA,123519,4807.038,X,01131.000,E,1,08,0.9,1,M,1,M,,")) == null,
                "reject invalid GGA hemisphere");
        check(GpsData.parseGga(sentence("GPRMC,123519,4807.038,N,01131.000,E,1,08,0.9,1,M,1,M,,")) == null,
                "other sentence types cannot masquerade as GGA");
        check(GpsData.parseGga(null) == null && GpsData.parseGga("$" + "A".repeat(300)) == null,
                "absent and oversized NMEA input rejected");
        Random random = new Random(17);
        for (int i = 0; i < 5000; i++) {
            StringBuilder noise = new StringBuilder();
            for (int j = random.nextInt(180); j > 0; j--) noise.append((char) random.nextInt(256));
            GpsData.parseGga(noise.toString());
        }
        check(true, "bounded malformed NMEA fuzz input never throws");
        System.out.println("ALL " + count + " GPS CHECKS PASSED");
    }
}
