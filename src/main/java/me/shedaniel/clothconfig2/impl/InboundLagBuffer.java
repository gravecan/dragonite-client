package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public final class InboundLagBuffer {

    private static final int MAX_QUEUE = 128;

    private final Queue<Packet<?>> queue = new ConcurrentLinkedQueue<>();
    private long holdUntil;
    private boolean holding;

    public void clear() {
        queue.clear();
        holding = false;
        holdUntil = 0L;
    }

    public int queuedCount() {
        return queue.size();
    }

    public void dropOldest() {
        queue.poll();
    }

    public boolean isHolding() {
        return holding && System.currentTimeMillis() < holdUntil;
    }

    public void beginHold(long durationMs) {
        holding = true;
        holdUntil = System.currentTimeMillis() + Math.max(1L, durationMs);
    }

    public void enqueue(Packet<?> packet) {
        while (queue.size() >= MAX_QUEUE) {
            queue.poll();
        }
        queue.offer(packet);
    }

    public void tick(MinecraftClient mc) {
        if (!holding || mc.getNetworkHandler() == null) {
            return;
        }
        if (System.currentTimeMillis() < holdUntil) {
            return;
        }
        holding = false;
        flush(mc);
    }

    public void flush(MinecraftClient mc) {
        flushCoalesced(mc);
    }

    
    public void flushCoalesced(MinecraftClient mc) {
        holding = false;
        if (mc.getNetworkHandler() == null) {
            queue.clear();
            return;
        }
        List<Packet<?>> batch = new ArrayList<>();
        while (!queue.isEmpty()) {
            batch.add(queue.poll());
        }
        if (batch.isEmpty()) {
            return;
        }
        int start = Math.max(0, batch.size() - Math.max(1, batch.size() / 3));
        for (int i = start; i < batch.size(); i++) {
            apply(mc, batch.get(i));
        }
    }

    private static void apply(MinecraftClient mc, Packet<?> packet) {
        try {
            @SuppressWarnings("unchecked")
            Packet<net.minecraft.network.listener.ClientPlayPacketListener> clientPacket =
                    (Packet<net.minecraft.network.listener.ClientPlayPacketListener>) packet;
            clientPacket.apply(mc.getNetworkHandler());
        } catch (Throwable ignored) {
        }
    }
}
