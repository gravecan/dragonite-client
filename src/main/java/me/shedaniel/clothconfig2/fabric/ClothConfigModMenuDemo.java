package me.shedaniel.clothconfig2.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.gui.DragoniteGlassScreen;
import net.minecraft.text.Text;


public class ClothConfigModMenuDemo implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new DragoniteGlassScreen(Text.empty());
    }
}
