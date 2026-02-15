package com.mohuia.better_looting.client;

import com.mohuia.better_looting.BetterLooting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BetterLooting.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyInit.TOGGLE_FILTER);
        event.register(KeyInit.PICKUP);
        event.register(KeyInit.OPEN_CONFIG);
        event.register(KeyInit.TOGGLE_AUTO);
        event.register(KeyInit.SHOW_OVERLAY);
        event.register(KeyInit.SCROLL_DOWN);
        event.register(KeyInit.SCROLL_UP);
    }
}
