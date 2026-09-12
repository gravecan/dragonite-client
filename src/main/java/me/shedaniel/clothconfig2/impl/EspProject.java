package me.shedaniel.clothconfig2.impl;



import net.minecraft.client.MinecraftClient;

import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import org.joml.Vector4f;





public final class EspProject {



    private static final Matrix4f VIEW_PROJ = new Matrix4f();

    private static final ThreadLocal<Vector4f> CLIP_SCRATCH = ThreadLocal.withInitial(Vector4f::new);



    private static volatile boolean captured;

    private static volatile int scaledWidth;

    private static volatile int scaledHeight;



    private EspProject() {}



    public static void capture(Matrix4f projection, Matrix4f position, int sw, int sh) {

        VIEW_PROJ.set(projection).mul(position);

        scaledWidth = sw;

        scaledHeight = sh;

        captured = true;

    }



    public static void clear() {

        captured = false;

    }



    public static boolean isCaptured() {

        return captured;

    }



    public static float[] worldToScreen(Vec3d world, MinecraftClient mc, boolean clipBounds) {
        if (!captured || world == null || mc == null) {
            return null;
        }

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Vector4f clip = CLIP_SCRATCH.get().set(
                (float) (world.x - camPos.x),
                (float) (world.y - camPos.y),
                (float) (world.z - camPos.z),
                1.0f
        );
        VIEW_PROJ.transform(clip);

        float w = clip.w;
        if (w <= 0.001f) {
            return null;
        }

        float ndcX = clip.x / w;
        float ndcY = clip.y / w;
        float ndcZ = clip.z / w;

        if (clipBounds && (ndcX < -1.25f || ndcX > 1.25f || ndcY < -1.25f || ndcY > 1.25f
                || ndcZ < -0.05f || ndcZ > 1.05f)) {
            return null;
        }

        int sw = scaledWidth > 0 ? scaledWidth : mc.getWindow().getScaledWidth();
        int sh = scaledHeight > 0 ? scaledHeight : mc.getWindow().getScaledHeight();

        float screenX = (ndcX * 0.5f + 0.5f) * sw;
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * sh;

        if (Float.isNaN(screenX) || Float.isNaN(screenY)
                || Float.isInfinite(screenX) || Float.isInfinite(screenY)) {
            return null;
        }

        return new float[]{screenX, screenY};
    }

    public static float[] worldToScreenDir(Vec3d world, MinecraftClient mc) {
        if (!captured || world == null || mc == null) return null;

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Vector4f clip = CLIP_SCRATCH.get().set(
                (float) (world.x - camPos.x),
                (float) (world.y - camPos.y),
                (float) (world.z - camPos.z),
                1.0f
        );
        VIEW_PROJ.transform(clip);

        float w = clip.w;
        boolean behind = w <= 0.001f;

        float ndcX = clip.x;
        float ndcY = clip.y;

        if (behind) {
            ndcX *= -1f;
            ndcY *= -1f;
        }

        float absW = Math.max(0.0001f, Math.abs(w));
        ndcX /= absW;
        ndcY /= absW;

        int sw = scaledWidth > 0 ? scaledWidth : mc.getWindow().getScaledWidth();
        int sh = scaledHeight > 0 ? scaledHeight : mc.getWindow().getScaledHeight();

        float screenX = (ndcX * 0.5f + 0.5f) * sw;
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * sh;

        if (Float.isNaN(screenX) || Float.isNaN(screenY)
                || Float.isInfinite(screenX) || Float.isInfinite(screenY)) {
            return null;
        }

        return new float[]{screenX, screenY, behind ? 1f : 0f};
    }
}


