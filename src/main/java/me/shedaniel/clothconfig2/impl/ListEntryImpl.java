package me.shedaniel.clothconfig2.impl;



import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.network.PlayerListEntry;

import net.minecraft.entity.Entity;

import net.minecraft.entity.EquipmentSlot;

import net.minecraft.entity.player.PlayerEntity;

import net.minecraft.item.ItemStack;

import net.minecraft.util.math.Vec3d;



import java.util.*;

import java.util.concurrent.CopyOnWriteArrayList;



public class ListEntryImpl extends ConfigCategoryImpl {



    private final BooleanToggleBuilder standingCheck;



    private final CopyOnWriteArrayList<PlayerEntity> botList = new CopyOnWriteArrayList<>();



    private final Map<Integer, Vec3d> lastPos = new HashMap<>();

    private final Map<Integer, Vec3d> lastVelocity = new HashMap<>();

    private final Map<Integer, Float> lastYaw = new HashMap<>();

    private final Map<Integer, Float> lastPitch = new HashMap<>();

    private final Map<Integer, Integer> stillTicks = new HashMap<>();

    private final Map<Integer, Integer> spawnTicks = new HashMap<>();

    private final Map<Integer, Integer> invalidRotations = new HashMap<>();

    private final Map<Integer, Integer> teleportCount = new HashMap<>();

    private final Set<Integer> confirmedBots = new HashSet<>();

    private final Map<Integer, Integer> entityAge = new HashMap<>();



    private final Map<Integer, Integer> swingCount = new HashMap<>();

    private final Map<Integer, Integer> hurtTime = new HashMap<>();

    private final Map<Integer, Integer> groundSpooferCount = new HashMap<>();

    private final Map<Integer, Long> lastInteractTime = new HashMap<>();

    private final Map<Integer, Integer> interactCount = new HashMap<>();

    private final Map<UUID, Long> uuidCache = new HashMap<>();

    private final Map<Integer, Integer> airTicks = new HashMap<>();

    private final Map<Integer, Integer> suspiciousTicks = new HashMap<>();

    private final Map<Integer, Integer> soundCount = new HashMap<>();

    private final Map<Integer, Long> lastPacketTime = new HashMap<>();

    private final Map<Integer, Integer> packetIrregularity = new HashMap<>();

    private final Map<Integer, Vec3d> spawnPos = new HashMap<>();

    private final Map<Integer, Long> spawnTime = new HashMap<>();



    private final Map<Integer, Integer> exactStillTicks = new HashMap<>();



    private final Set<UUID> suspectSet = new HashSet<>();

    private final Set<UUID> botUuidSet = new HashSet<>();

    private final Map<Integer, List<ItemStack>> prevArmor = new HashMap<>();

    private static final EquipmentSlot[] ARMOR_SLOTS = {

        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET

    };



    public ListEntryImpl() {

        super("AntiBot", "Detects and ignores fake player entities spawned by anti-cheats.", Cat.COMBAT);

        setTooltip("Detects and ignores fake player entities spawned by anticheats");

        setEnabled(true);



        standingCheck = new BooleanToggleBuilder(

                "Standing Check", "Flag players standing perfectly still for too long", false);

        addSetting(standingCheck);

    }



    public void tick() {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null) {

            clearAll();

            return;

        }



        if (mc.player == null) return;



        Map<Integer, Vec3d> currentPos = new HashMap<>();

        Map<Integer, Vec3d> currentVelocity = new HashMap<>();

        Map<Integer, Float> currentYaw = new HashMap<>();

        Map<Integer, Float> currentPitch = new HashMap<>();



        for (Entity e : mc.world.getPlayers()) {

            if (e == mc.player) continue;

            int id = e.getId();



            entityAge.merge(id, 1, Integer::sum);



            if (!spawnTicks.containsKey(id)) {

                spawnTicks.put(id, 0);

            } else {

                spawnTicks.merge(id, 1, Integer::sum);

            }



            currentPos.put(id, e.getPos());

            Vec3d prevPos = lastPos.get(id);

            if (prevPos != null) {

                currentVelocity.put(id, e.getPos().subtract(prevPos));

            }



            currentYaw.put(id, e.getHeadYaw());

            currentPitch.put(id, e.getPitch());



            boolean posStill = prevPos != null && prevPos.squaredDistanceTo(e.getPos()) < 0.001;

            Float prevYaw = lastYaw.get(id);

            Float prevPitch = lastPitch.get(id);

            boolean yawStill = prevYaw != null && Math.abs(prevYaw - e.getHeadYaw()) < 0.01f;

            boolean pitchStill = prevPitch != null && Math.abs(prevPitch - e.getPitch()) < 0.01f;



            if (posStill && yawStill && pitchStill) {

                stillTicks.merge(id, 1, Integer::sum);

            } else {

                stillTicks.put(id, 0);

            }



            if (prevPos != null) {

                double exactDelta = prevPos.squaredDistanceTo(e.getPos());

                boolean exactYaw = prevYaw != null && e.getHeadYaw() == prevYaw;

                boolean exactPitch = prevPitch != null && e.getPitch() == prevPitch;



                if (exactDelta == 0.0 && exactYaw && exactPitch) {

                    exactStillTicks.merge(id, 1, Integer::sum);

                } else {

                    exactStillTicks.put(id, 0);

                }

            }



            if (prevYaw != null) {

                float yawDiff = Math.abs(e.getHeadYaw() - prevYaw);

                if (yawDiff > 90 && yawDiff < 270) {

                    invalidRotations.merge(id, 1, Integer::sum);

                }

            }



            if (prevPos != null) {

                double dist = prevPos.squaredDistanceTo(e.getPos());

                if (dist > 100) {

                    teleportCount.merge(id, 1, Integer::sum);

                }

            }



            if (!e.isOnGround()) {

                airTicks.merge(id, 1, Integer::sum);

            } else {

                airTicks.put(id, 0);

            }



            if (!spawnPos.containsKey(id)) {

                spawnPos.put(id, e.getPos());

                spawnTime.put(id, System.currentTimeMillis());

            }

        }



        lastPos.clear();

        lastPos.putAll(currentPos);

        lastVelocity.clear();

        lastVelocity.putAll(currentVelocity);

        lastYaw.clear();

        lastYaw.putAll(currentYaw);

        lastPitch.clear();

        lastPitch.putAll(currentPitch);



        stillTicks.keySet().retainAll(currentPos.keySet());

        spawnTicks.keySet().retainAll(currentPos.keySet());

        invalidRotations.keySet().retainAll(currentPos.keySet());

        teleportCount.keySet().retainAll(currentPos.keySet());

        interactCount.keySet().retainAll(currentPos.keySet());

        airTicks.keySet().retainAll(currentPos.keySet());

        suspiciousTicks.keySet().retainAll(currentPos.keySet());

        exactStillTicks.keySet().retainAll(currentPos.keySet());



        botList.clear();

        if (mc.world != null && mc.player != null) {

            for (PlayerEntity player : mc.world.getPlayers()) {

                if (player == mc.player) continue;

                if (isBot(player)) {

                    botList.add(player);

                }

            }

        }

    }



    public boolean isBot(Entity entity) {

        if (!isEnabled() || !(entity instanceof PlayerEntity player)) return false;

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.getNetworkHandler() == null || mc.world == null) return false;



        int id = entity.getId();

        if (confirmedBots.contains(id)) return true;



        return isBotCheck(player, mc);

    }



    private boolean isBotCheck(PlayerEntity player, MinecraftClient mc) {

        int id = player.getId();

        String name = player.getName().getString();

        UUID uuid = player.getUuid();



        int uuidVersion = uuid.version();

        if (uuidVersion == 1 || uuidVersion == 2) return true;



        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(uuid);

        boolean missingTab = entry == null;

        int age = entityAge.getOrDefault(id, 0);

        if (standingCheck.get()) {

            if (missingTab && age > 100) {

                int exactStill = exactStillTicks.getOrDefault(id, 0);

                if (exactStill > 50) return true;

            }

        }

        if (name.contains("NPC") || name.startsWith("[ZNPC]")) return true;




        Long sTime = spawnTime.get(id);

        if (sTime != null && System.currentTimeMillis() - sTime < 1500) {

            if (invalidRotations.getOrDefault(id, 0) > 3) return true;

        }



        int air = airTicks.getOrDefault(id, 0);

        Vec3d vel = lastVelocity.get(id);



        if (player.isOnGround() && air > 40) {

            if (vel != null && Math.abs(vel.y) < 0.001) return true;

        }



        if (standingCheck.get()) {

            int exactStill = exactStillTicks.getOrDefault(id, 0);

            boolean inVehicle = player.getVehicle() != null;

            boolean inWater = player.isTouchingWater();



            if (exactStill > 200 && age > 200 && !inVehicle && !inWater) {

                return true;

            }

        }



        if (isDuplicateProfile(player.getUuid(), name, mc)) {

            return true;

        }



        boolean isNameBot = name.contains("NPC") || name.startsWith("[ZNPC]");

        if (isNameBot && missingTab) return true;



        if (standingCheck.get() && isFullyEquippedUnenchanted(player) && missingTab) {

            int exactStill = exactStillTicks.getOrDefault(id, 0);

            if (exactStill > 30) return true;

        }



        return false;

    }



    private boolean isFullyEquippedUnenchanted(PlayerEntity player) {

        for (EquipmentSlot slot : ARMOR_SLOTS) {

            ItemStack stack = player.getEquippedStack(slot);

            if (!isArmorItem(stack) || stack.hasEnchantments()) {

                return false;

            }

        }

        return true;

    }



    private boolean isArmorItem(ItemStack stack) {

        if (stack.isEmpty()) return false;



        String itemName = stack.getItem().toString().toLowerCase();

        return itemName.contains("helmet") || itemName.contains("chestplate")

            || itemName.contains("leggings") || itemName.contains("boots");

    }



    private boolean isDuplicateProfile(UUID uuid, String name, MinecraftClient mc) {

        if (mc.getNetworkHandler() == null) return false;

        return mc.getNetworkHandler().getPlayerList().stream()

                .filter(entry -> entry.getProfile().getName().equals(name) && !entry.getProfile().getId().equals(uuid))

                .count() >= 1;

    }



    public void onSwing(PlayerEntity player) {

        swingCount.merge(player.getId(), 1, Integer::sum);

        lastInteractTime.put(player.getId(), System.currentTimeMillis());

        interactCount.merge(player.getId(), 1, Integer::sum);

    }



    public void onInteract(PlayerEntity player) {

        lastInteractTime.put(player.getId(), System.currentTimeMillis());

        interactCount.merge(player.getId(), 1, Integer::sum);

    }



    private void clearAll() {

        lastPos.clear();

        lastVelocity.clear();

        lastYaw.clear();

        lastPitch.clear();

        stillTicks.clear();

        spawnTicks.clear();

        invalidRotations.clear();

        teleportCount.clear();

        confirmedBots.clear();

        uuidCache.clear();

        airTicks.clear();

        suspiciousTicks.clear();

        soundCount.clear();

        lastPacketTime.clear();

        packetIrregularity.clear();

        spawnPos.clear();

        spawnTime.clear();

        exactStillTicks.clear();



        suspectSet.clear();

        botUuidSet.clear();

        prevArmor.clear();

    }



    public void onSound(PlayerEntity player) {

        soundCount.merge(player.getId(), 1, Integer::sum);

    }



    public void onPacket(PlayerEntity player) {

        int id = player.getId();

        long now = System.currentTimeMillis();

        Long last = lastPacketTime.get(id);



        if (last != null) {

            long diff = now - last;



            if (diff < 10 || diff > 150) {

                packetIrregularity.merge(id, 1, Integer::sum);

            }

        }

        lastPacketTime.put(id, now);

    }



    @Override

    public void onDisable() {

        clearAll();

    }



    public void markAsPacketBot(PlayerEntity player) {

        confirmedBots.add(player.getId());

    }

}


