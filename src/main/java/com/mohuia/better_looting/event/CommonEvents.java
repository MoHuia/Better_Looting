package com.mohuia.better_looting.event;

import com.mohuia.better_looting.BetterLooting;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 这是一个事件订阅类（Event Subscriber）。
 * 作用：监听游戏内的通用事件（不仅仅是客户端，服务端也会生效）。
 *
 * @Mod.EventBusSubscriber: 这是一个 Forge 注解。
 *   modid: 指定属于哪个模组（必须跟主类 ID 一致）。
 *   bus: 指定监听哪个总线。这里是 FORGE 总线（负责游戏内发生的事件，如移动、攻击、拾取）。
 */
@Mod.EventBusSubscriber(modid = BetterLooting.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents {

    /**
     * @SubscribeEvent: 这是一个监听器方法。
     * 只要游戏里发生了 EntityItemPickupEvent（实体物品被拾取事件），
     * Forge 就会自动调用这个方法。
     *
     * 该事件在以下情况触发：
     * 1. 玩家走近掉落物（原版自动拾取范围约 1 格）。
     * 2. 任何实体尝试拾取物品（比如村民捡面包、狐狸捡浆果）。
     */
    @SubscribeEvent
    public static void onVanillaPickup(EntityItemPickupEvent event) {

        // 1. 检查是谁触发了这个事件？
        // event.getEntity() 返回尝试拾取物品的实体。
        // 我们只想禁止“玩家”自动拾取，不想影响村民、漏斗矿车等其他机制。
        if (event.getEntity() instanceof Player) {

            // 2. 核心逻辑：取消事件！
            // setCanceled(true) 告诉游戏：“这就当没发生过一样。”
            //
            // 结果：
            // - 物品不会进入玩家背包。
            // - 物品实体不会消失。
            // - 不会播放“啵”的拾取音效。
            //
            // 只有通过我们自己的网络包（按 F 键触发的那一套逻辑），
            // 才能真正执行拾取操作。
            event.setCanceled(true);
        }
    }
}
