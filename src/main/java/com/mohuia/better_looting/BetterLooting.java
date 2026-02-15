package com.mohuia.better_looting;

import com.mohuia.better_looting.client.overlay.Overlay;
import com.mohuia.better_looting.config.Config;
import com.mohuia.better_looting.network.NetworkHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BetterLooting.MODID)
public class BetterLooting {
    public static final String MODID = "better_looting";

    //构造函数，启动时先执行这个，模组初始化阶段
    public BetterLooting(FMLJavaModLoadingContext context) {
        //事件总线
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        //注册配置文件
        context.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    //通用设置
    private void commonSetup(final FMLCommonSetupEvent event) {
        //线程安全的保险措施
        //初始化网络通道
        event.enqueueWork(NetworkHandler::register);
    }

    //客户端运行
    private void clientSetup(final FMLClientSetupEvent event) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            //游戏内事件总线，将UI类注册到游戏内事件的总线上
            MinecraftForge.EVENT_BUS.register(new Overlay());
        });
    }
}
