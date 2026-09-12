package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;

public class Config_Hitsound extends ConfigCategoryImpl {
    public static Config_Hitsound INSTANCE;

    public final EnumSelectorBuilder soundMode;
    public final DoubleFieldBuilder volume;
    public final BooleanToggleBuilder replaceCrit;
    public final BooleanToggleBuilder killSound;

    private Entity lastAttackedEntity;
    private long lastAttackTime;

    public Config_Hitsound() {
        super("Hitsound", "Play custom sound when hitting entities", Cat.RENDER);
        INSTANCE = this;

        soundMode = new EnumSelectorBuilder("Sound Mode", "Which hitsound to play", "Bell", "Bell", "Magic", "Pew", "Phew", "Run");
        volume = new DoubleFieldBuilder("Volume", "Volume of hitsounds", 1.0, 0.0, 1.0, 0.05);
        replaceCrit = new BooleanToggleBuilder("Mute Vanilla Attack", "Mutes vanilla attack sounds", true);
        killSound = new BooleanToggleBuilder("Kill Sound", "Play sound on target death", true);

        addSetting(soundMode);
        addSetting(volume);
        addSetting(replaceCrit);
        addSetting(killSound);
    }

    public void setLastAttacked(Entity entity) {
        this.lastAttackedEntity = entity;
        this.lastAttackTime = System.currentTimeMillis();
    }

    public Entity getLastAttackedEntity() {
        if (System.currentTimeMillis() - lastAttackTime > 5000) {
            return null;
        }
        return lastAttackedEntity;
    }

    public void playHitSound() {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        
        String mode = soundMode.get().toLowerCase();
        if ("none".equals(mode)) return;

        float vol = (float) volume.get();
        float pitch = 1.0F + (float) ((Math.random() - 0.5) * 0.1);

        mc.getSoundManager().play(
            PositionedSoundInstance.master(
                SoundEvent.of(Identifier.of("cloth-config2:hit." + mode)),
                pitch,
                vol
            )
        );
    }

    public void playKillSound() {
        if (!isEnabled() || !killSound.get()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        float vol = (float) volume.get();

        mc.getSoundManager().play(
            PositionedSoundInstance.master(
                SoundEvent.of(Identifier.of("cloth-config2:kill.kill")),
                1.0F,
                vol
            )
        );
    }

    public void tick(MinecraftClient mc) {
        
    }
}
