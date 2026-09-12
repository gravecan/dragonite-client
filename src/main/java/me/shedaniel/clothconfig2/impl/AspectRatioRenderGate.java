package me.shedaniel.clothconfig2.impl;


public final class AspectRatioRenderGate {

    private static final ThreadLocal<Integer> HAND_PASS_DEPTH = ThreadLocal.withInitial(() -> 0);

    private AspectRatioRenderGate() {}

    public static void beginHandPass() {
        HAND_PASS_DEPTH.set(HAND_PASS_DEPTH.get() + 1);
    }

    public static void endHandPass() {
        int depth = HAND_PASS_DEPTH.get();
        if (depth <= 1) {
            HAND_PASS_DEPTH.remove();
        } else {
            HAND_PASS_DEPTH.set(depth - 1);
        }
    }

    public static boolean isHandPass() {
        return HAND_PASS_DEPTH.get() > 0;
    }
}
