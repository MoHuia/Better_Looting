package com.mohuia.better_looting.client;

import com.mohuia.better_looting.BetterLooting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端模组事件监听器
 * <p>
 * 该类监听 <b>Mod Event Bus</b> (模组总线) 上的事件，而不是 Forge Event Bus。
 * 主要用于处理客户端特有的初始化任务，例如注册按键绑定、实体渲染器或图层定义。
 * </p>
 *
 * @author Mohuia
 */
@Mod.EventBusSubscriber(modid = BetterLooting.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    /**
     * 注册按键绑定
     * <p>
     * 在游戏启动初始化阶段触发，将 {@link KeyInit} 中定义的按键注册到原版控制选项中。
     * </p>
     *
     * @param event 按键映射注册事件
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyInit.TOGGLE_FILTER);
        event.register(KeyInit.PICKUP);
        event.register(KeyInit.OPEN_CONFIG);
        event.register(KeyInit.TOGGLE_AUTO);
        event.register(KeyInit.SHOW_OVERLAY);

        // 滚动与选择相关
        event.register(KeyInit.SCROLL_DOWN);
        event.register(KeyInit.SCROLL_UP);

        // 【修复】之前漏掉了修饰键的注册，必须注册后才能生效
        event.register(KeyInit.SCROLL_MODIFIER);
    }
}
