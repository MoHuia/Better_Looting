package com.mohuia.better_looting.client;

import com.mohuia.better_looting.client.filter.FilterWhitelist;
import com.mohuia.better_looting.config.Config;
import com.mohuia.better_looting.config.ConfigScreen;
import com.mohuia.better_looting.network.NetworkHandler;
import com.mohuia.better_looting.network.C2S.PacketBatchPickup;
import com.mohuia.better_looting.network.C2S.PacketPickupItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.stream.Collectors;

public class Core {
    public static final Core INSTANCE = new Core();

    public enum FilterMode { ALL, RARE_ONLY }

    private static final double PICKUP_EXPAND_XZ = 1.0;
    private static final double PICKUP_EXPAND_Y = 0.5;

    private static final int LONG_PRESS_THRESHOLD = 15; // 长按阈值 (ticks)

    // 核心数据
    private List<ItemEntity> nearbyItems = new ArrayList<>();
    private final Set<Item> cachedInventoryItems = new HashSet<>();
    private int selectedIndex = 0;
    private int targetScrollOffset = 0;

    // 状态控制
    private int tickCounter = 0;
    private FilterMode filterMode = FilterMode.ALL;

    // 自动拾取相关状态
    private boolean isAutoMode = false;
    private int autoPickupCooldown = 0;

    // 长按交互状态
    private int pickupHoldTicks = 0;
    private boolean hasTriggeredBatch = false;

    // 按键滚动计时器
    private int scrollKeyHoldTime = 0;

    // 排序器：稀有度 > 附魔 > 名字 > ID
    private final Comparator<ItemEntity> stableComparator = (e1, e2) -> {
        ItemStack s1 = e1.getItem();
        ItemStack s2 = e2.getItem();
        Rarity r1 = s1.getRarity();
        Rarity r2 = s2.getRarity();
        int rDiff = r2.ordinal() - r1.ordinal();
        if (rDiff != 0) return rDiff;
        boolean enc1 = s1.isEnchanted();
        boolean enc2 = s2.isEnchanted();
        if (enc1 != enc2) return enc1 ? -1 : 1;
        int nameDiff = s1.getHoverName().getString().compareTo(s2.getHoverName().getString());
        if (nameDiff != 0) return nameDiff;
        return Integer.compare(e1.getId(), e2.getId());
    };

    private Core() {
        MinecraftForge.EVENT_BUS.register(this);
        FilterWhitelist.INSTANCE.init();
    }

    // --- Getters ---
    public List<ItemEntity> getNearbyItems() { return nearbyItems; }
    public int getSelectedIndex() { return selectedIndex; }
    public int getTargetScrollOffset() { return targetScrollOffset; }
    public boolean hasItems() { return !nearbyItems.isEmpty(); }
    public FilterMode getFilterMode() { return filterMode; }
    public boolean isAutoMode() { return isAutoMode; }

    public float getPickupProgress() {
        if (!hasItems() || hasTriggeredBatch) return 0.0f;
        return Mth.clamp((float) pickupHoldTicks / LONG_PRESS_THRESHOLD, 0.0f, 1.0f);
    }

    // --- Static Helpers ---
    public static boolean shouldIntercept() {
        return INSTANCE.hasItems() || INSTANCE.hasTriggeredBatch;
    }

    public boolean isItemInInventory(Item item) { return cachedInventoryItems.contains(item); }
    public static void performPickup() { INSTANCE.doPickup(); }
    public static void performBatchPickup() { INSTANCE.doBatchPickup(); }

    // --- Logic Control ---

    public void toggleFilterMode() {
        if (filterMode == FilterMode.ALL) filterMode = FilterMode.RARE_ONLY;
        else filterMode = FilterMode.ALL;
        selectedIndex = 0;
        targetScrollOffset = 0;
    }

    public void toggleAutoMode() {
        isAutoMode = !isAutoMode;
        autoPickupCooldown = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Component message;
            if (isAutoMode) {
                message = Component.translatable("message.better_looting.auto_on")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            } else {
                message = Component.translatable("message.better_looting.auto_off")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            }
            mc.player.displayClientMessage(message, true);
        }
    }

    private void doPickup() {
        if (!nearbyItems.isEmpty() && selectedIndex >= 0 && selectedIndex < nearbyItems.size()) {
            ItemEntity target = nearbyItems.get(selectedIndex);
            if (target.isAlive()) NetworkHandler.sendToServer(new PacketPickupItem(target.getId()));
        }
    }

    private void doBatchPickup() {
        doBatchPickupInternal(this.nearbyItems, false);
    }

    private void doBatchPickupInternal(List<ItemEntity> entitiesToPickup, boolean isAuto) {
        if (entitiesToPickup.isEmpty()) return;
        List<Integer> ids = entitiesToPickup.stream()
                .filter(ItemEntity::isAlive)
                .map(ItemEntity::getId)
                .collect(Collectors.toList());

        if (!ids.isEmpty()) NetworkHandler.sendToServer(new PacketBatchPickup(ids, isAuto));
    }

    // --- Events ---

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        this.tick();
        this.handleInput();
    }

    private void handleInput() {
        while (KeyInit.TOGGLE_FILTER.consumeClick()) {
            toggleFilterMode();
        }
        while (KeyInit.OPEN_CONFIG.consumeClick()) {
            Minecraft.getInstance().setScreen(new ConfigScreen());
        }
        while (KeyInit.TOGGLE_AUTO.consumeClick()) {
            toggleAutoMode();
        }

        // 按键滚动逻辑 (支持长按连发)
        boolean upDown = KeyInit.SCROLL_UP.isDown();
        boolean downDown = KeyInit.SCROLL_DOWN.isDown();

        if (upDown || downDown) {
            scrollKeyHoldTime++;
            if (scrollKeyHoldTime == 1 || (scrollKeyHoldTime > 10 && scrollKeyHoldTime % 3 == 0)) {
                if (upDown) {
                    performScroll(1.0); // 向上
                } else {
                    performScroll(-1.0); // 向下
                }
            }
        } else {
            scrollKeyHoldTime = 0;
        }
    }

    @SubscribeEvent
    public void onScroll(InputEvent.MouseScrollingEvent event) {
        // 如果在配置界面，不允许 HUD 滚动
        if (Minecraft.getInstance().screen instanceof ConfigScreen) return;

        // 如果列表为空或只有一个，不进行滚动拦截，允许原版行为
        if (nearbyItems.size() <= 1) return;

        // Shift 按下时通常保留原版功能（如潜行等），不拦截
        if (Screen.hasShiftDown()) return;

        // --- [修改] 滚轮模式冲突判断逻辑 ---
        Config.ScrollMode mode = Config.CLIENT.scrollMode.get();
        boolean allowHudScroll = false;

        switch (mode) {
            case ALWAYS:
                // 默认：只要 HUD 有内容就允许
                allowHudScroll = true;
                break;
            case KEY_BIND:
                // 特定按键：只有按下修饰键时才允许 HUD 滚动
                allowHudScroll = KeyInit.SCROLL_MODIFIER.isDown();
                break;
            case STAND_STILL:
                // 静止模式：检测玩家是否移动
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    double dx = mc.player.getX() - mc.player.xo;
                    double dz = mc.player.getZ() - mc.player.zo;
                    // 速度平方很小则视为静止
                    allowHudScroll = (dx * dx + dz * dz) < 0.0001;
                }
                break;
        }

        // 如果条件不满足（比如要静止但玩家在跑，或者没按修饰键），则 return，
        // 让事件继续传递给 Minecraft 原版处理（切换快捷栏）。
        if (!allowHudScroll) return;
        // ------------------------------------

        double scrollDelta = event.getScrollDelta();
        if (scrollDelta != 0) {
            performScroll(scrollDelta);
            event.setCanceled(true); // 拦截事件，防止物品栏切换
        }
    }

    // 通用滚动逻辑
    private void performScroll(double scrollDelta) {
        if (nearbyItems.size() <= 1) return;

        if (scrollDelta > 0) {
            selectedIndex--; // 向上/前
        } else {
            selectedIndex++; // 向下/后
        }

        // 循环索引
        if (selectedIndex < 0) selectedIndex = nearbyItems.size() - 1;
        if (selectedIndex >= nearbyItems.size()) selectedIndex = 0;

        updateScrollOffset();
    }

    // --- Main Loop ---

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            nearbyItems.clear();
            cachedInventoryItems.clear();
            pickupHoldTicks = 0;
            hasTriggeredBatch = false;
            return;
        }

        if (mc.screen instanceof ConfigScreen) return;

        tickCounter++;

        // 1. 更新背包缓存
        if (tickCounter % 10 == 0) {
            updateInventoryCache(mc);
        }

        // 2. 扫描实体
        AABB area = mc.player.getBoundingBox().inflate(PICKUP_EXPAND_XZ, PICKUP_EXPAND_Y, PICKUP_EXPAND_XZ);
        List<ItemEntity> found = mc.level.getEntitiesOfClass(ItemEntity.class, area, entity ->
                entity.isAlive() && !entity.getItem().isEmpty()
        );

        // 3. 执行过滤
        if (filterMode == FilterMode.RARE_ONLY) {
            found.removeIf(entity -> {
                ItemStack stack = entity.getItem();
                if (FilterWhitelist.INSTANCE.contains(stack.getItem())) return false;

                return stack.getRarity() == Rarity.COMMON
                        && !stack.isEnchanted()
                        && !Utils.shouldShowTooltip(stack);
            });
        }

        // 4. 执行自动拾取
        if (isAutoMode && !found.isEmpty()) {
            if (autoPickupCooldown > 0) {
                autoPickupCooldown--;
            } else {
                doBatchPickupInternal(found, true);
                autoPickupCooldown = 5;
            }
        } else {
            autoPickupCooldown = 0;
        }

        // 5. 排序
        found.sort(stableComparator);
        nearbyItems = found;

        // 6. 索引安全检查
        if (nearbyItems.isEmpty()) {
            selectedIndex = 0;
            targetScrollOffset = 0;
        } else {
            selectedIndex = Math.max(0, Math.min(selectedIndex, nearbyItems.size() - 1));
            updateScrollOffset();
        }

        // 7. 处理长按/短按逻辑
        handlePickupLogic();
    }

    private void handlePickupLogic() {
        if (KeyInit.PICKUP.isDown()) {
            if (!nearbyItems.isEmpty() || hasTriggeredBatch) {
                pickupHoldTicks++;
            }

            if (pickupHoldTicks >= LONG_PRESS_THRESHOLD && !hasTriggeredBatch) {
                if (!nearbyItems.isEmpty()) {
                    doBatchPickup();
                    hasTriggeredBatch = true;
                }
            }
        } else {
            if (pickupHoldTicks > 0) {
                if (pickupHoldTicks < LONG_PRESS_THRESHOLD && !hasTriggeredBatch && !nearbyItems.isEmpty()) {
                    doPickup();
                }
            }
            pickupHoldTicks = 0;
            hasTriggeredBatch = false;
        }
    }

    private void updateInventoryCache(Minecraft mc) {
        cachedInventoryItems.clear();
        if (mc.player == null) return;
        mc.player.getInventory().items.forEach(s -> { if(!s.isEmpty()) cachedInventoryItems.add(s.getItem()); });
        mc.player.getInventory().offhand.forEach(s -> { if(!s.isEmpty()) cachedInventoryItems.add(s.getItem()); });
        mc.player.getInventory().armor.forEach(s -> { if(!s.isEmpty()) cachedInventoryItems.add(s.getItem()); });
    }

    private void updateScrollOffset() {
        int maxVisible = Config.CLIENT.visibleRows.get().intValue();
        if (maxVisible < 1) maxVisible = 1;

        if (nearbyItems.size() <= maxVisible) {
            targetScrollOffset = 0;
            return;
        }

        if (selectedIndex < targetScrollOffset) {
            targetScrollOffset = selectedIndex;
        } else if (selectedIndex >= targetScrollOffset + maxVisible) {
            targetScrollOffset = selectedIndex - maxVisible + 1;
        }

        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, nearbyItems.size() - maxVisible));
    }
}
