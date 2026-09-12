package me.shedaniel.clothconfig2.impl;



import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.input.KeyboardInput;

import net.minecraft.client.network.ClientPlayerEntity;

import net.minecraft.client.option.Perspective;

import net.minecraft.network.packet.Packet;

import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;

import net.minecraft.util.math.MathHelper;

import net.minecraft.util.math.Vec3d;





public class Config_FreeCam extends ConfigCategoryImpl {

    public static Config_FreeCam INSTANCE;



    private static final ThreadLocal<Integer> ANCHOR_SEND_DEPTH = ThreadLocal.withInitial(() -> 0);



    private final DoubleFieldBuilder speed;

    private final BooleanToggleBuilder showBody;

    private final BooleanToggleBuilder disableOnDamage;



    private Vec3d pos = Vec3d.ZERO;

    private Vec3d prevPos = Vec3d.ZERO;

    private Vec3d bodyPos = Vec3d.ZERO;

    private float camYaw;

    private float camPitch;

    private float bodyYaw;

    private float bodyPitch;

    private boolean bodyOnGround;

    private float lastHealth = 20f;

    private boolean initialized;



    public Config_FreeCam() {

        super("Freecam", "Fly the camera while your body stays in the world", Cat.MOVEMENT);

        INSTANCE = this;

        speed = new DoubleFieldBuilder("Speed", "Camera fly speed", 1.5, 0.2, 5.0, 0.1);

        showBody = new BooleanToggleBuilder("Show Body", "Third person view of your player", true);

        disableOnDamage = new BooleanToggleBuilder("Disable On Damage", "Turn off when hurt", true);

        addSetting(speed);

        addSetting(showBody);

        addSetting(disableOnDamage);

    }



    @Override

    public void onEnable() {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {

            return;

        }

        if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {

            pos = mc.gameRenderer.getCamera().getPos();

            camYaw = mc.gameRenderer.getCamera().getYaw();

            camPitch = mc.gameRenderer.getCamera().getPitch();

        } else {

            pos = mc.player.getEyePos();

            camYaw = mc.player.getYaw();

            camPitch = mc.player.getPitch();

        }

        prevPos = pos;

        bodyPos = mc.player.getPos();

        bodyYaw = mc.player.getYaw();

        bodyPitch = mc.player.getPitch();

        bodyOnGround = mc.player.isOnGround();

        lastHealth = mc.player.getHealth();

        initialized = true;

        if (showBody.get() && mc.options != null) {

            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);

        }

    }



    @Override

    public void onDisable() {

        initialized = false;

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.options != null) {

            mc.options.setPerspective(Perspective.FIRST_PERSON);

        }

    }



    public void addLookDelta(float deltaYaw, float deltaPitch) {

        camYaw += deltaYaw * 0.15f;

        camPitch = MathHelper.clamp(camPitch + deltaPitch * 0.15f, -90f, 90f);

    }



    public float getCamYaw() {

        return camYaw;

    }



    public float getCamPitch() {

        return camPitch;

    }



    public float getBodyYaw() {

        return bodyYaw;

    }



    public float getBodyPitch() {

        return bodyPitch;

    }



    public void applyInputLock(KeyboardInput input) {

        if (!isEnabled() || !initialized) {

            return;

        }

        input.movementForward = 0f;

        input.movementSideways = 0f;

        input.jumping = false;

        input.sneaking = false;

    }



    public void freezeBody(ClientPlayerEntity player) {

        if (!isEnabled() || !initialized || player == null) {

            return;

        }

        player.setVelocity(Vec3d.ZERO);

        player.fallDistance = 0f;

        player.setPosition(bodyPos);

        player.setYaw(bodyYaw);

        player.setPitch(bodyPitch);

        player.headYaw = bodyYaw;

        if (player.isSprinting()) {

            player.setSprinting(false);

        }

    }



    public void tick(MinecraftClient mc) {

        if (!isEnabled() || !initialized || mc.player == null) {

            return;

        }



        if (disableOnDamage.get() && mc.player.getHealth() < lastHealth) {

            setEnabled(false);

            return;

        }

        lastHealth = mc.player.getHealth();



        freezeBody(mc.player);



        float spd = (float) speed.get();

        if (mc.options.sprintKey.isPressed()) {

            spd *= 2.2f;

        }



        float yawRad = camYaw * MathHelper.RADIANS_PER_DEGREE;

        float pitchRad = camPitch * MathHelper.RADIANS_PER_DEGREE;

        double sin = MathHelper.sin(yawRad);

        double cos = MathHelper.cos(yawRad);

        double pitchCos = MathHelper.cos(pitchRad);



        double forward = 0;

        double sideways = 0;

        if (mc.options.forwardKey.isPressed()) forward += 1;

        if (mc.options.backKey.isPressed()) forward -= 1;

        if (mc.options.leftKey.isPressed()) sideways += 1;

        if (mc.options.rightKey.isPressed()) sideways -= 1;



        double vx = 0;

        double vy = 0;

        double vz = 0;

        if (forward != 0 || sideways != 0) {

            double len = Math.sqrt(forward * forward + sideways * sideways);

            forward /= len;

            sideways /= len;

            vx = (-sin * pitchCos * forward + cos * sideways) * spd;

            vz = (cos * pitchCos * forward + sin * sideways) * spd;

            vy = -MathHelper.sin(pitchRad) * forward * spd;

        }

        if (mc.options.jumpKey.isPressed()) {

            vy += spd;

        }

        if (mc.options.sneakKey.isPressed()) {

            vy -= spd;

        }



        prevPos = pos;

        pos = pos.add(vx, vy, vz);



        if (showBody.get() && mc.options != null

                && mc.options.getPerspective() == Perspective.FIRST_PERSON) {

            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);

        }

    }



    public Vec3d getInterpolatedPos(float tickDelta) {

        if (!initialized) {

            return null;

        }

        return prevPos.lerp(pos, tickDelta);

    }



    public static boolean isAnchorSend() {

        return ANCHOR_SEND_DEPTH.get() > 0;

    }



    

    public boolean handleOutbound(Packet<?> packet) {
        if (isAnchorSend() || !isEnabled() || !initialized) {
            return false;
        }
        if (!(packet instanceof PlayerMoveC2SPacket p)) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return false;
        }

        // Mirror the packet type sent by vanilla client but modify the position/rotation to body state
        int depth = ANCHOR_SEND_DEPTH.get();
        ANCHOR_SEND_DEPTH.set(depth + 1);
        try {
            boolean changePos = p.changesPosition();
            boolean changeLook = p.changesLook();
            Packet<?> mirror;
            if (changePos && changeLook) {
                mirror = new PlayerMoveC2SPacket.Full(
                        bodyPos.x, bodyPos.y, bodyPos.z,
                        bodyYaw, bodyPitch,
                        p.isOnGround()
                );
            } else if (changePos) {
                mirror = new PlayerMoveC2SPacket.PositionAndOnGround(
                        bodyPos.x, bodyPos.y, bodyPos.z,
                        p.isOnGround()
                );
            } else if (changeLook) {
                mirror = new PlayerMoveC2SPacket.LookAndOnGround(
                        bodyYaw, bodyPitch,
                        p.isOnGround()
                );
            } else {
                mirror = new PlayerMoveC2SPacket.OnGroundOnly(
                        p.isOnGround()
                );
            }
            mc.getNetworkHandler().sendPacket(mirror);
        } finally {
            if (depth == 0) {
                ANCHOR_SEND_DEPTH.remove();
            } else {
                ANCHOR_SEND_DEPTH.set(depth);
            }
        }
        return true;
    }



    private void pushAnchorPacket(MinecraftClient mc) {

        int depth = ANCHOR_SEND_DEPTH.get();

        ANCHOR_SEND_DEPTH.set(depth + 1);

        try {

            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(

                    bodyPos.x,

                    bodyPos.y,

                    bodyPos.z,

                    bodyYaw,

                    bodyPitch,

                    bodyOnGround

            ));

        } finally {

            if (depth == 0) {

                ANCHOR_SEND_DEPTH.remove();

            } else {

                ANCHOR_SEND_DEPTH.set(depth);

            }

        }

    }



    public boolean handleInbound(Packet<?> packet) {

        if (!isEnabled()) {

            return false;

        }

        if (packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket) {

            setEnabled(false);

        }

        return false;

    }

}


