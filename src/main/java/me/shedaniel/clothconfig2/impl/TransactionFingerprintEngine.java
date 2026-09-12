package me.shedaniel.clothconfig2.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class TransactionFingerprintEngine {

    private static final int SAMPLE_TARGET = 5;

    private static final List<Integer> samples = new ArrayList<>(SAMPLE_TARGET);
    private static boolean collecting;
    private static String lastResolved;
    private static boolean listening;

    private TransactionFingerprintEngine() {}

    public static void setListening(boolean active) {
        listening = active;
        if (active) {
            beginCapture();
        }
    }

    public static void resetSession() {
        samples.clear();
        collecting = false;
        lastResolved = null;
    }

    public static void beginCapture() {
        if (!listening) {
            return;
        }
        samples.clear();
        collecting = true;
        lastResolved = null;
    }

    public static void recordPing(int parameter) {
        if (!listening || !collecting) {
            return;
        }
        samples.add(parameter);
        if (samples.size() >= SAMPLE_TARGET) {
            collecting = false;
            Config_ProtocolScan.onSamplesReady();
        }
    }

    public static boolean hasEnoughSamples() {
        return samples.size() >= SAMPLE_TARGET;
    }

    public static String getLastResolved() {
        return lastResolved;
    }

    public static String resolveAndCache(String serverHost) {
        lastResolved = classify(serverHost);
        return lastResolved;
    }

    static String classify(String serverHost) {
        if (samples.size() < SAMPLE_TARGET) {
            return null;
        }

        String host = serverHost == null ? "" : serverHost.toLowerCase(Locale.ROOT);
        if (host.contains("hypixel.net")) {
            return "Watchdog";
        }

        int head = samples.get(0);
        int[] deltas = pairwiseDelta(samples);

        if (allEqual(deltas)) {
            String uniform = classifyUniformDelta(deltas[0], head);
            if (uniform != null) {
                return uniform;
            }
        }

        if (samples.size() >= 2 && samples.get(0).equals(samples.get(1)) && tailStepsByOne(samples, 2)) {
            return "Verus";
        }

        if (deltas.length >= 2 && deltas[0] >= 100 && deltas[1] == -1 && allEqualFrom(deltas, 2, -1)) {
            return "Polar";
        }

        if (head < -3000 && containsZero(samples)) {
            return "Intave";
        }

        if (matchesPrefix(samples, -30767, -30766, -25767) && tailStepsByOne(samples, 3)) {
            return "Old Vulcan";
        }

        return "Unknown";
    }

    private static String classifyUniformDelta(int step, int firstId) {
        if (step == 1) {
            if (inRange(firstId, -23772, -23762)) {
                return "Vulcan";
            }
            if (inRange(firstId, 95, 105) || inRange(firstId, -20005, -19995)) {
                return "Matrix";
            }
            if (inRange(firstId, -32773, -32762)) {
                return "Grizzly";
            }
            return "Verus";
        }
        if (step == -1) {
            if (inRange(firstId, -8287, -8280)) {
                return "Errata";
            }
            if (firstId < -3000) {
                return "Intave";
            }
            if (inRange(firstId, -5, 0)) {
                return "Grim";
            }
            if (inRange(firstId, -3000, -2995)) {
                return "Karhu";
            }
            return "Polar";
        }
        return null;
    }

    private static int[] pairwiseDelta(List<Integer> ids) {
        int[] out = new int[ids.size() - 1];
        for (int i = 1; i < ids.size(); i++) {
            out[i - 1] = ids.get(i) - ids.get(i - 1);
        }
        return out;
    }

    private static boolean allEqual(int[] values) {
        if (values.length == 0) {
            return false;
        }
        int v = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] != v) {
                return false;
            }
        }
        return true;
    }

    private static boolean allEqualFrom(int[] values, int start, int expected) {
        for (int i = start; i < values.length; i++) {
            if (values[i] != expected) {
                return false;
            }
        }
        return true;
    }

    private static boolean tailStepsByOne(List<Integer> ids, int startIndex) {
        for (int i = startIndex + 1; i < ids.size(); i++) {
            if (ids.get(i) - ids.get(i - 1) != 1) {
                return false;
            }
        }
        return ids.size() > startIndex + 1;
    }

    private static boolean containsZero(List<Integer> ids) {
        for (int id : ids) {
            if (id == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPrefix(List<Integer> ids, int... prefix) {
        if (ids.size() < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (!ids.get(i).equals(prefix[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean inRange(int value, int low, int high) {
        return value >= low && value <= high;
    }
}
