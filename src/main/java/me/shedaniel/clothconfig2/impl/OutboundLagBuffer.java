package me.shedaniel.clothconfig2.impl;

import me.shedaniel.math.impl.mixin.ClientConnectionInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public final class OutboundLagBuffer {

    private static final int MAX_QUEUE = 512;
    
    private static final double MAX_STEP_HORIZONTAL = 0.28;
    private static final double MAX_STEP_VERTICAL = 0.42;
    private static final ThreadLocal<Integer> FLUSH_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final Queue<Packet<?>> queue = new ConcurrentLinkedQueue<>();
    private Vec3d renderPos = Vec3d.ZERO;
    private boolean hasRenderPos;

    public static boolean isFlushing() {
        return FLUSH_DEPTH.get() > 0;
    }

    public void clear() {
        queue.clear();
        hasRenderPos = false;
        renderPos = Vec3d.ZERO;
    }

    public void commitRenderPos(MinecraftClient mc) {
        if (mc != null && mc.player != null) {
            renderPos = mc.player.getPos();
            hasRenderPos = true;
        }
    }

    public Vec3d getRenderPos() {
        return hasRenderPos ? renderPos : null;
    }

    public boolean hasRenderPos() {
        return hasRenderPos;
    }

    public boolean hasAnchor() {
        return hasRenderPos();
    }

    public Vec3d getAnchor() {
        return getRenderPos();
    }

    public void captureAnchor(MinecraftClient mc) {
        commitRenderPos(mc);
    }

    public void enqueue(Packet<?> packet) {
        while (queue.size() >= MAX_QUEUE) {
            dropOldestMovement();
        }
        queue.offer(packet);
    }

    public int queuedCount() {
        return queue.size();
    }

    
    public void flushCoalesced(MinecraftClient mc) {
        flushStepped(mc);
    }

    
    public void flushStepped(MinecraftClient mc) {
        if (mc == null || mc.getNetworkHandler() == null || mc.player == null) {
            queue.clear();
            return;
        }
        ClientConnection connection = mc.getNetworkHandler().getConnection();
        if (connection == null) {
            queue.clear();
            return;
        }

        List<Packet<?>> rest = new ArrayList<>();
        while (!queue.isEmpty()) {
            Packet<?> p = queue.poll();
            if (!isMovementPacket(p)) {
                rest.add(p);
            }
        }

        int depth = FLUSH_DEPTH.get();
        FLUSH_DEPTH.set(depth + 1);
        try {
            if (hasRenderPos) {
                Vec3d from = renderPos;
                Vec3d to = mc.player.getPos();
                Vec3d next = stepToward(from, to);
                sendDirect(mc, new PlayerMoveC2SPacket.Full(
                        next.x,
                        next.y,
                        next.z,
                        mc.player.getYaw(),
                        mc.player.getPitch(),
                        mc.player.isOnGround()
                ));
                renderPos = next;
                hasRenderPos = true;
            } else {
                commitRenderPos(mc);
            }
            for (Packet<?> p : rest) {
                sendDirect(mc, p);
            }
        } finally {
            if (depth == 0) {
                FLUSH_DEPTH.remove();
            } else {
                FLUSH_DEPTH.set(depth);
            }
        }
    }

    
    public void flushUntilSynced(MinecraftClient mc, int maxSteps) {
        if (mc == null || mc.player == null || !hasRenderPos) {
            flushStepped(mc);
            return;
        }
        int steps = 0;
        while (steps < maxSteps && renderPos.squaredDistanceTo(mc.player.getPos()) > 0.04) {
            flushStepped(mc);
            steps++;
        }
    }

    public void flush(MinecraftClient mc) {
        flushStepped(mc);
    }

    private static Vec3d stepToward(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double scale = 1.0;
        if (horizontal > MAX_STEP_HORIZONTAL) {
            scale = Math.min(scale, MAX_STEP_HORIZONTAL / horizontal);
        }
        if (Math.abs(dy) > MAX_STEP_VERTICAL) {
            scale = Math.min(scale, MAX_STEP_VERTICAL / Math.abs(dy));
        }
        if (scale >= 1.0) {
            return to;
        }
        return from.add(dx * scale, dy * scale, dz * scale);
    }

    private void dropOldestMovement() {
        Packet<?> oldestMove = null;
        List<Packet<?>> keep = new ArrayList<>();
        while (!queue.isEmpty()) {
            Packet<?> p = queue.poll();
            if (oldestMove == null && isMovementPacket(p)) {
                oldestMove = p;
            } else {
                keep.add(p);
            }
        }
        for (Packet<?> p : keep) {
            queue.offer(p);
        }
    }

    private static boolean isMovementPacket(Packet<?> packet) {
        return packet instanceof PlayerMoveC2SPacket || packet instanceof VehicleMoveC2SPacket;
    }

    private static void sendDirect(MinecraftClient mc, Packet<?> packet) {
        ClientConnection connection = mc.getNetworkHandler().getConnection();
        if (connection == null) {
            return;
        }
        ((ClientConnectionInvoker) (Object) connection).cloth$sendDirect(packet, null, false);
    }
}
