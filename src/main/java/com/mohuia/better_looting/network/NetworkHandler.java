package com.mohuia.better_looting.network;

import com.mohuia.better_looting.BetterLooting;
import com.mohuia.better_looting.network.C2S.PacketBatchPickup;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通信处理器。
 * <p>
 * 负责注册模组的网络通道（Channel）和所有数据包（Packets）。
 * 协议版本用于确保客户端和服务端版本一致。
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BetterLooting.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /**
     * 注册所有网络包。
     * 注意：ID 必须唯一且顺序一致。
     */
    public static void register() {
        int id = 0;

        // 注册批量/自动拾取包 (C2S)
        INSTANCE.registerMessage(id++,
                PacketBatchPickup.class,
                PacketBatchPickup::toBytes,
                PacketBatchPickup::new,
                PacketBatchPickup::handle
        );
    }

    /**
     * 发送数据包到服务端。
     * @param msg 数据包实例
     */
    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }
}
