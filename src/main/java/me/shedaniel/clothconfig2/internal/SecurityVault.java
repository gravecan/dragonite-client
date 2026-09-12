package me.shedaniel.clothconfig2.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import me.shedaniel.clothconfig2.internal.secure.JavaHwidGatherer;

public final class SecurityVault {

    private static final long OPAQUE_SIN_BITS = Double.doubleToRawLongBits(Math.sin(1.0));

    private static final int TAG_SLOTS = 16;
    private static final int[] slotTags = new int[TAG_SLOTS];

    private static volatile int tagA;
    private static volatile int tagB;
    private static volatile int tagC;
    private static volatile int magic1;
    private static volatile int magic2;

    private static volatile int mixA;
    private static volatile int mixB;
    private static volatile int mixC;
    private static volatile int epoch;
    private static volatile long blend;
    private static volatile int canary0;
    private static volatile int canary1;
    private static volatile int canary2;

    private SecurityVault() {}

    static void arm(String sessionToken, String hwid) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            if (sessionToken != null) {
                sha.update(sessionToken.getBytes(StandardCharsets.UTF_8));
            }
            if (hwid != null) {
                sha.update(hwid.getBytes(StandardCharsets.UTF_8));
            }
            sha.update((byte) 0x44);
            sha.update((byte) 0x6B);
            
            String machine = JavaHwidGatherer.getMachineGuid();
            if (machine != null) {
                sha.update(machine.getBytes(StandardCharsets.UTF_8));
            }
            
            byte[] digest = sha.digest();
            mixA = readInt(digest, 0);
            mixB = readInt(digest, 4);
            mixC = readInt(digest, 8);
            epoch = readInt(digest, 12);
            blend = ((long) readInt(digest, 16) << 32) | (readInt(digest, 20) & 0xFFFFFFFFL);

            int base = mixA ^ mixB ^ mixC ^ epoch;
            tagA = base ^ readInt(digest, 24);
            if (tagA == 0) {
                tagA = 0x7A4E12C3;
            }
            magic1 = readInt(digest, 28) ^ 0xC0FFEE42;
            magic2 = readInt(digest, 24) ^ readInt(digest, 28) ^ 0xB16B00B5;
            tagB = tagA ^ magic1;
            tagC = rotate(tagA, 7) ^ magic2;

            canary0 = rotate(base ^ 0x51C0FFEE, 7);
            canary1 = rotate(base ^ 0xBADC0FFE, 13);
            canary2 = rotate(base ^ 0x0B1A2E3D, 3);
            for (int i = 0; i < TAG_SLOTS; i++) {
                slotTags[i] = computeTag(i);
            }
        } catch (Exception e) {
            disarm();
        }
    }

    static void disarm() {
        tagA = tagB = tagC = magic1 = magic2 = 0;
        mixA = mixB = mixC = epoch = 0;
        blend = 0L;
        canary0 = canary1 = canary2 = 0;
        for (int i = 0; i < TAG_SLOTS; i++) {
            slotTags[i] = 0;
        }
    }

    
    public static boolean invariantHolds() {
        return evaluateInvariantTriplet(tagA, tagB, tagC, magic1, magic2);
    }

    static boolean evaluateInvariantTriplet(int a, int b, int c, int m1, int m2) {
        if (a == 0) {
            return false;
        }
        if ((a ^ b) != m1) {
            return false;
        }
        return (Integer.rotateLeft(a, 7) ^ c) == m2;
    }

    public static boolean isArmed() {
        return evaluateArmedState(invariantHolds(), canary0, opaqueEnvironmentOk());
    }

    static boolean evaluateArmedState(boolean invariant, int canary, boolean opaqueOk) {
        return invariant && canary != 0 && opaqueOk;
    }

    
    public static boolean opaqueEnvironmentOk() {
        return Double.doubleToRawLongBits(Math.sin(1.0)) == OPAQUE_SIN_BITS;
    }

    
    public static int bleedTag() {
        return tagA & 0xFF;
    }

    public static int derive(int slot) {
        if (!isArmed()) {
            return slot ^ 0xDEADBEEF;
        }
        int bucket = (int) ((System.nanoTime() >>> 22) & 0x3F);
        return mixCore(slot) ^ bucket;
    }

    public static boolean verifyCombo(int slot) {
        int idx = Math.floorMod(slot, TAG_SLOTS);
        return evaluateComboGate(
                opaqueEnvironmentOk(),
                invariantHolds(),
                SessionHandler.getInstance().authEpochActive(),
                computeTag(idx),
                slotTags[idx]);
    }

    static boolean evaluateComboGate(boolean opaqueOk, boolean invariant,
                                     boolean epochActive, int computed,
                                     int stored) {
        if (!opaqueOk || !invariant || !epochActive) {
            return false;
        }
        return computed == stored;
    }

    private static int computeTag(int slot) {
        int a = mixCore(slot);
        int b = mixCore(slot + 11);
        int c = mixCore(slot + 23);
        return (a ^ b) + (c ^ rotate(canary2, slot & 7));
    }

    private static int mixCore(int slot) {
        int x = mixA ^ rotate(mixB + slot * 0x9E3779B9, slot & 15);
        x ^= mixC ^ (epoch << (slot & 7));
        x ^= (int) (blend ^ (slot * 0x517CC1B7L));
        x ^= rotate(canary0 ^ canary1, (slot + 3) & 31);
        x ^= tagA & 0xFF;
        x ^= BuildFingerprint.mixSeed(slot);
        return x;
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24)
                | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8)
                | (b[off + 3] & 0xFF);
    }

    private static int rotate(int v, int bits) {
        return Integer.rotateLeft(v, bits & 31);
    }


}
