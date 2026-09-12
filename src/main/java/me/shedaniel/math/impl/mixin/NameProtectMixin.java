package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_NameProtect;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class NameProtectMixin {
    
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
                    at = @At("HEAD"), argsOnly = true)
    private Text modifyMessage(Text message) {
        if (Config_NameProtect.INSTANCE != null && Config_NameProtect.INSTANCE.isEnabled()) {
            String text = message.getString();
            String protectedText = Config_NameProtect.INSTANCE.protectName(text);
            if (!text.equals(protectedText)) {
                return Text.literal(protectedText).setStyle(message.getStyle());
            }
        }
        return message;
    }
}
