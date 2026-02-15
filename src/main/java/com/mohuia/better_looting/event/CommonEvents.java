package com.mohuia.better_looting.event;

import com.mohuia.better_looting.BetterLooting;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 游戏通用事件监听器。
 * <p>
 * 负责处理服务端的逻辑事件（如物品拾取判定）。
 * 这里的逻辑会在客户端和服务端同时生效，但主要目的是为了拦截服务端的默认行为。
 */
@Mod.EventBusSubscriber(modid = BetterLooting.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents {

    /**
     * 拦截原版物品拾取事件。
     * <p>
     * <b>原理：</b>
     * 当实体（玩家、生物）尝试拾取地面上的 {@link net.minecraft.world.entity.item.ItemEntity} 时触发。
     * 我们需要取消这个事件，防止玩家只要走近物品就自动捡起。
     * 这样才能让我们的 "按键拾取" 逻辑独占控制权。
     *
     * @param event 实体拾取物品事件
     */
    @SubscribeEvent
    public static void onVanillaPickup(EntityItemPickupEvent event) {
        // 只拦截 "玩家" 的自动拾取，不影响村民、漏斗矿车、海豚等生物的交互
        if (event.getEntity() instanceof Player) {
            // 设置为 canceled = true，告诉 Forge 和原版游戏：
            // "这个物品不要进入背包，不要播放拾取音效，不要消失。"
            // 真正的数据操作将由 PacketBatchPickup 网络包接管。
            event.setCanceled(true);
        }
    }
}
