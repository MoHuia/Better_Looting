package com.mohuia.better_looting.client;

import com.mohuia.better_looting.client.core.LootScanner;
import com.mohuia.better_looting.client.core.PickupHandler;
import com.mohuia.better_looting.client.core.VisualItemEntry;
import com.mohuia.better_looting.client.filter.FilterWhitelist;
import com.mohuia.better_looting.config.Config;
import com.mohuia.better_looting.config.ConfigScreen;
import com.mohuia.better_looting.network.NetworkHandler;
import com.mohuia.better_looting.network.C2S.PacketBatchPickup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户端核心控制器 (单例模式).
 * 负责调度整个客户端的核心业务逻辑，基于 MVC 架构设计：
 *
 * Model (状态): 维护 {@link #nearbyItems} (周围掉落物) 和 {@link #selectedIndex} (选中状态)。
 * Controller (事件):监听 Tick 和 Input 事件，委托给 {@link LootScanner} 和 {@link PickupHandler} 处理。
 * @author Mohuia
 */
public class Core {

    /** 全局唯一实例 */
    public static final Core INSTANCE = new Core();

    /** 物品过滤模式 */
    public enum FilterMode {
        /** 显示所有物品 */
        ALL,
        /** 仅显示稀有度较高或白名单内的物品 */
        RARE_ONLY
    }

    // =========================================
    //               核心组件
    // =========================================

    private final PickupHandler pickupHandler = new PickupHandler();

    /** 当前扫描到的周围掉落物实体列表 (已合并渲染条目) */
    private List<VisualItemEntry> nearbyItems = new ArrayList<>();

    // =========================================
    //               UI 与 控制状态
    // =========================================

    private int selectedIndex = 0;
    /** 目标滚动偏移量，用于实现平滑滚动动画 */
    private int targetScrollOffset = 0;

    private FilterMode filterMode = FilterMode.ALL;
    private boolean isAutoMode = false;

    /** 记录滚动按键按下的时长，用于处理长按连续滚动 */
    private int scrollKeyHoldTime = 0;

    private Core() {
        // 在构造时注册 Forge 事件总线，确保 tick 和 input 事件被监听
        MinecraftForge.EVENT_BUS.register(this);
        FilterWhitelist.INSTANCE.init();
    }

    // =========================================
    //               Public API (供 HUD 渲染使用)
    // =========================================

    public List<VisualItemEntry> getNearbyItems() { return nearbyItems; }
    public int getSelectedIndex() { return selectedIndex; }
    public int getTargetScrollOffset() { return targetScrollOffset; }
    public boolean hasItems() { return !nearbyItems.isEmpty(); }
    public FilterMode getFilterMode() { return filterMode; }
    public boolean isAutoMode() { return isAutoMode; }

    /**
     * 检查玩家背包中是否含有指定物品.
     *
     * 直接遍历背包而非使用缓存，避免了同步问题。
     * 由于客户端背包槽位较少，此操作性能开销极低。
     *
     * @param item 目标物品
     * @return 如果背包中存在该物品则返回 true
     */
    public boolean isItemInInventory(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前拾取动作的进度 (0.0f - 1.0f).
     * 用于 HUD 渲染长按进度圈。
     */
    public float getPickupProgress() {
        return hasItems() ? pickupHandler.getProgress() : 0.0f;
    }

    /**
     * 判断是否应该拦截原版交互逻辑 (如左键攻击/右键使用).
     * 当模组正在处理列表交互时返回 true。
     */
    public static boolean shouldIntercept() {
        return INSTANCE.hasItems() || INSTANCE.pickupHandler.isInteracting();
    }

    // =========================================
    //               事件循环逻辑
    // =========================================

    /**
     * 客户端主循环逻辑.
     * 负责扫描物品、处理自动拾取以及更新输入状态。
     *
     * @param event Tick 事件，仅在 {@link TickEvent.Phase#END} 执行以确保逻辑完整性。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            nearbyItems.clear();
            return;
        }

        // 避免在配置界面操作时触发游戏内逻辑
        if (mc.screen instanceof ConfigScreen) return;

        // 1. 执行扫描
        this.nearbyItems = LootScanner.scan(mc, this.filterMode);

        // 2. 处理自动拾取
        if (isAutoMode && !nearbyItems.isEmpty()) {
            if (pickupHandler.canAutoPickup()) {
                List<ItemEntity> allEntities = new ArrayList<>();
                for (VisualItemEntry entry : nearbyItems) {
                    allEntities.addAll(entry.getSourceEntities());
                }
                sendBatchPickup(allEntities, true);
            }
        } else {
            pickupHandler.resetAutoCooldown();
        }

        // 3. 校验状态与处理输入
        validateSelection();
        handleInputLogic();
    }

    /**
     * 处理按键输入逻辑 (功能键切换、拾取操作、键盘滚动).
     */
    private void handleInputLogic() {
        // 功能键切换
        while (KeyInit.TOGGLE_FILTER.consumeClick()) toggleFilterMode();
        while (KeyInit.OPEN_CONFIG.consumeClick()) Minecraft.getInstance().setScreen(new ConfigScreen());
        while (KeyInit.TOGGLE_AUTO.consumeClick()) toggleAutoMode();

        // 拾取动作判定
        boolean isFKeyDown = KeyInit.PICKUP.isDown();
        boolean isShiftDown = Screen.hasShiftDown();

        PickupHandler.PickupAction action = pickupHandler.tickInput(isFKeyDown, isShiftDown, !nearbyItems.isEmpty());

        switch (action) {
            case SINGLE:
                sendSinglePickup();
                break;
            case BATCH:
                List<ItemEntity> allEntities = new ArrayList<>();
                for (VisualItemEntry entry : nearbyItems) {
                    allEntities.addAll(entry.getSourceEntities());
                }
                sendBatchPickup(allEntities, false);
                break;
            default:
                break;
        }

        handleKeyboardScroll();
    }

    // =========================================
    //               滚动与视图逻辑
    // =========================================

    /**
     * 拦截并处理鼠标滚轮事件.
     */
    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (shouldIgnoreScroll()) return;

        double scrollDelta = event.getScrollDelta();
        if (scrollDelta != 0) {
            performScroll(scrollDelta);
            event.setCanceled(true); // 阻止原版快捷栏滚动
        }
    }

    /**
     * 判断当前上下文是否允许模组接管滚轮事件.
     * 依据配置文件 (ScrollMode) 和玩家状态决定。
     */
    private boolean shouldIgnoreScroll() {
        if (Minecraft.getInstance().screen instanceof ConfigScreen) return true;
        if (nearbyItems.size() <= 1) return true;
        if (Screen.hasShiftDown()) return true;

        Config.ScrollMode mode = Config.CLIENT.scrollMode.get();
        if (mode == Config.ScrollMode.ALWAYS) return false;
        if (mode == Config.ScrollMode.KEY_BIND) return !KeyInit.SCROLL_MODIFIER.isDown();
        if (mode == Config.ScrollMode.STAND_STILL) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return true;
            // 简单位移判定：如果移动距离很小则视为静止
            double dx = mc.player.getX() - mc.player.xo;
            double dz = mc.player.getZ() - mc.player.zo;
            return (dx * dx + dz * dz) >= 0.0001;
        }
        return true;
    }

    private void performScroll(double delta) {
        if (nearbyItems.size() <= 1) return;
        // delta > 0 为向上滚，索引减小；delta < 0 为向下滚，索引增加
        selectedIndex += (delta > 0) ? -1 : 1;
        validateSelection();
    }

    /**
     * 处理键盘辅助滚动 (支持长按加速).
     */
    private void handleKeyboardScroll() {
        boolean up = KeyInit.SCROLL_UP.isDown();
        boolean down = KeyInit.SCROLL_DOWN.isDown();

        if (up || down) {
            scrollKeyHoldTime++;
            // 逻辑：第1 tick触发一次，超过10 tick后每3 tick触发一次
            if (scrollKeyHoldTime == 1 || (scrollKeyHoldTime > 10 && scrollKeyHoldTime % 3 == 0)) {
                performScroll(up ? 1.0 : -1.0);
            }
        } else {
            scrollKeyHoldTime = 0;
        }
    }

    /**
     * 校验并修正选中索引，同时计算滚动视口偏移 (Scroll Offset).
     * 包含循环滚动逻辑和视口跟随逻辑。
     */
    private void validateSelection() {
        if (nearbyItems.isEmpty()) {
            selectedIndex = 0;
            targetScrollOffset = 0;
            return;
        }

        // 1. 循环滚动
        if (selectedIndex < 0) selectedIndex = nearbyItems.size() - 1;
        if (selectedIndex >= nearbyItems.size()) selectedIndex = 0;

        double visibleRows = Math.max(1.0, Config.CLIENT.visibleRows.get());

        // 2. 计算视口偏移 (TargetScrollOffset)
        if (nearbyItems.size() <= visibleRows) {
            targetScrollOffset = 0;
            return;
        }

        // 视口向下推移
        if (selectedIndex + 1 > targetScrollOffset + visibleRows) {
            targetScrollOffset = (int) Math.ceil(selectedIndex - visibleRows + 1);
        }
        // 视口向上推移
        if (selectedIndex < targetScrollOffset) {
            targetScrollOffset = selectedIndex;
        }

        // 边界钳制
        int maxOffset = (int) Math.ceil(Math.max(0, nearbyItems.size() - visibleRows));
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxOffset));
    }

    // =========================================
    //               网络与交互
    // =========================================

    /**
     * 发送单次拾取请求.
     * 逻辑：对当前选中的条目，按距离排序后，发送 limitToMaxStack=true 的请求。
     */
    private void sendSinglePickup() {
        if (selectedIndex >= 0 && selectedIndex < nearbyItems.size()) {
            VisualItemEntry entry = nearbyItems.get(selectedIndex);

            List<ItemEntity> candidates = new ArrayList<>(entry.getSourceEntities());
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // 优先拾取最近的
                candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
            }

            List<Integer> ids = candidates.stream()
                    .filter(ItemEntity::isAlive)
                    .map(ItemEntity::getId)
                    .collect(Collectors.toList());

            if (!ids.isEmpty()) {
                NetworkHandler.sendToServer(new PacketBatchPickup(ids, false, true));
            }
        }
    }

    /**
     * 发送批量拾取请求.
     * 逻辑：不限制数量，请求拾取列表中的所有实体。
     *
     * @param entities 目标实体列表
     * @param isAuto 是否由自动拾取触发 (服务端可能据此略过某些检查)
     */
    private void sendBatchPickup(List<ItemEntity> entities, boolean isAuto) {
        List<Integer> ids = entities.stream()
                .filter(ItemEntity::isAlive)
                .map(ItemEntity::getId)
                .collect(Collectors.toList());

        if (!ids.isEmpty()) {
            NetworkHandler.sendToServer(new PacketBatchPickup(ids, isAuto, false));
        }
    }

    public void toggleFilterMode() {
        filterMode = (filterMode == FilterMode.ALL) ? FilterMode.RARE_ONLY : FilterMode.ALL;
        validateSelection();
    }

    public void toggleAutoMode() {
        isAutoMode = !isAutoMode;
        pickupHandler.resetAutoCooldown();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Component msg = isAutoMode
                    ? Component.translatable("message.better_looting.auto_on").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("message.better_looting.auto_off").withStyle(ChatFormatting.RED);
            mc.player.displayClientMessage(msg, true);
        }
    }
}
