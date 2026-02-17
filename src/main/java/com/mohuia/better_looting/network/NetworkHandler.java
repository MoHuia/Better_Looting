package com.mohuia.better_looting.network;

import com.mohuia.better_looting.BetterLooting;
import com.mohuia.better_looting.network.C2S.PacketBatchPickup;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通信管理器.
 * <p>
 * 负责注册模组的网络通道（Channel）和所有数据包（Packets）。
 * 只有注册在此处的数据包才能在客户端和服务端之间传输。
 */
public class NetworkHandler {
    // 协议版本号：当网络包结构发生破坏性变更时，应修改此版本号以防止版本不匹配的客户端连接
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BetterLooting.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals, // 客户端版本检查
            PROTOCOL_VERSION::equals  // 服务端版本检查
    );

    /**
     * 注册所有网络包.
     * <p>
     * ID 必须是唯一的 int 值，注册顺序在客户端和服务端必须完全一致。
     */
    public static void register() {
        int id = 0;

        // 注册批量拾取包 (C2S)
        INSTANCE.registerMessage(id++,
                PacketBatchPickup.class,
                PacketBatchPickup::toBytes, // 编码器
                PacketBatchPickup::new,     // 解码器
                PacketBatchPickup::handle   // 处理器
        );
    }

    /**
     * 发送数据包到服务端 (简便封装).
     *
     * @param msg 已注册的数据包实例
     */
    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }
}
