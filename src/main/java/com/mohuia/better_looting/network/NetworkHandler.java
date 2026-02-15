package com.mohuia.better_looting.network;

import com.mohuia.better_looting.BetterLooting;
import com.mohuia.better_looting.network.C2S.PacketBatchPickup;
import com.mohuia.better_looting.network.C2S.PacketPickupItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BetterLooting.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        // 注册单个拾取包
        INSTANCE.registerMessage(id++,
                PacketPickupItem.class,
                PacketPickupItem::toBytes,
                PacketPickupItem::new,
                PacketPickupItem::handle
        );

        // 注册批量拾取包
        INSTANCE.registerMessage(id++,
                PacketBatchPickup.class,
                PacketBatchPickup::toBytes,
                PacketBatchPickup::new,
                PacketBatchPickup::handle
        );
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }
}
