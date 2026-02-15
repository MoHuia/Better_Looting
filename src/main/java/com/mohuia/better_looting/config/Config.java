package com.mohuia.better_looting.config;

import com.mohuia.better_looting.BetterLooting;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 模组的核心配置类。
 * <p>
 * 使用 Forge 的 {@link ForgeConfigSpec} 构建配置规范。
 * 包含自动同步到文件的配置项以及用于渲染线程高速访问的“烘焙（Baked）”缓存。
 */
@Mod.EventBusSubscriber(modid = BetterLooting.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        // 构建配置规范与配置实例的配对
        final Pair<ClientConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    /** 激活模式枚举 */
    public enum ActivationMode { ALWAYS, LOOK_DOWN, STAND_STILL, KEY_HOLD, KEY_TOGGLE }
    /** 滚动模式枚举 */
    public enum ScrollMode { ALWAYS, KEY_BIND, STAND_STILL }

    /**
     * 客户端配置定义类。
     * 定义了配置文件的结构、默认值和取值范围。
     */
    public static class ClientConfig {
        // --- 默认常量定义 ---
        public static final double DEFAULT_OFFSET_X = 0.0;
        public static final double DEFAULT_OFFSET_Y = 0.0;
        public static final double DEFAULT_SCALE = 0.7;
        public static final int DEFAULT_WIDTH = 100;
        public static final double DEFAULT_ROWS = 4.5;
        public static final double DEFAULT_ALPHA = 0.9;
        public static final ActivationMode DEFAULT_MODE = ActivationMode.ALWAYS;
        public static final ScrollMode DEFAULT_SCROLL_MODE = ScrollMode.ALWAYS;
        public static final double DEFAULT_ANGLE = 45.0;

        // --- 配置项 (ConfigValue) ---
        public final ForgeConfigSpec.DoubleValue xOffset;
        public final ForgeConfigSpec.DoubleValue yOffset;
        public final ForgeConfigSpec.DoubleValue uiScale;
        public final ForgeConfigSpec.IntValue panelWidth;
        public final ForgeConfigSpec.DoubleValue visibleRows;
        public final ForgeConfigSpec.DoubleValue globalAlpha;
        public final ForgeConfigSpec.EnumValue<ActivationMode> activationMode;
        public final ForgeConfigSpec.EnumValue<ScrollMode> scrollMode;
        public final ForgeConfigSpec.DoubleValue lookDownAngle;

        ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.push("client"); // 开始 "client" 分类

            // defineInRange 确保用户手动修改配置文件时不会超出逻辑边界
            xOffset = builder.defineInRange("xOffset", DEFAULT_OFFSET_X, -3000.0, 3000.0);
            yOffset = builder.defineInRange("yOffset", DEFAULT_OFFSET_Y, -3000.0, 3000.0);
            uiScale = builder.defineInRange("uiScale", DEFAULT_SCALE, 0.1, 6.0);
            panelWidth = builder.defineInRange("panelWidth", DEFAULT_WIDTH, 100, 500);
            visibleRows = builder.defineInRange("visibleRows", DEFAULT_ROWS, 1.0, 20.0);
            globalAlpha = builder.defineInRange("globalAlpha", DEFAULT_ALPHA, 0.1, 1.0);
            activationMode = builder.defineEnum("activationMode", DEFAULT_MODE);
            scrollMode = builder.defineEnum("scrollMode", DEFAULT_SCROLL_MODE);
            lookDownAngle = builder.defineInRange("lookDownAngle", DEFAULT_ANGLE, 0.0, 90.0);

            builder.pop();
        }
    }

    /**
     * 烘焙缓存（Baked Cache）。
     * <p>
     * <b>设计目的：</b>
     * Forge 的 {@code ConfigValue.get()} 方法包含哈希查找和同步逻辑，开销相对较大。
     * 在高频调用场景（如 {@code RenderGuiEvent} 每帧渲染）中直接调用配置项会影响 FPS。
     * 此类将配置值缓存为原生字段（static fields），供渲染线程直接读取。
     */
    public static class Baked {
        public static double xOffset;
        public static double yOffset;
        public static float uiScale;
        public static int panelWidth;
        public static double visibleRows;
        public static float globalAlpha;
        public static ActivationMode activationMode;
        public static ScrollMode scrollMode;
        public static double lookDownAngle;

        /** 从 Forge 配置对象同步值到静态字段 */
        public static void refresh() {
            xOffset = CLIENT.xOffset.get();
            yOffset = CLIENT.yOffset.get();
            uiScale = CLIENT.uiScale.get().floatValue();
            panelWidth = CLIENT.panelWidth.get();
            visibleRows = CLIENT.visibleRows.get();
            globalAlpha = CLIENT.globalAlpha.get().floatValue();
            activationMode = CLIENT.activationMode.get();
            scrollMode = CLIENT.scrollMode.get();
            lookDownAngle = CLIENT.lookDownAngle.get();
        }
    }

    /**
     * 监听配置加载或重载事件。
     * 当配置文件被修改或模组初始化时，自动刷新 Baked 缓存。
     */
    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            Baked.refresh();
        }
    }
}
