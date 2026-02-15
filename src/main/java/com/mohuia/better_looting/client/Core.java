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

    private static final int LONG_PRESS_THRESHOLD = 15;

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

        boolean upDown = KeyInit.SCROLL_UP.isDown();
        boolean downDown = KeyInit.SCROLL_DOWN.isDown();

        if (upDown || downDown) {
            scrollKeyHoldTime++;
            if (scrollKeyHoldTime == 1 || (scrollKeyHoldTime > 10 && scrollKeyHoldTime % 3 == 0)) {
                if (upDown) {
                    performScroll(1.0);
                } else {
                    performScroll(-1.0);
                }
            }
        } else {
            scrollKeyHoldTime = 0;
        }
    }

    @SubscribeEvent
    public void onScroll(InputEvent.MouseScrollingEvent event) {
        if (Minecraft.getInstance().screen instanceof ConfigScreen) return;
        if (nearbyItems.size() <= 1) return;
        if (Screen.hasShiftDown()) return;

        Config.ScrollMode mode = Config.CLIENT.scrollMode.get();
        boolean allowHudScroll = false;

        switch (mode) {
            case ALWAYS:
                allowHudScroll = true;
                break;
            case KEY_BIND:
                allowHudScroll = KeyInit.SCROLL_MODIFIER.isDown();
                break;
            case STAND_STILL:
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    double dx = mc.player.getX() - mc.player.xo;
                    double dz = mc.player.getZ() - mc.player.zo;
                    allowHudScroll = (dx * dx + dz * dz) < 0.0001;
                }
                break;
        }

        if (!allowHudScroll) return;

        double scrollDelta = event.getScrollDelta();
        if (scrollDelta != 0) {
            performScroll(scrollDelta);
            event.setCanceled(true);
        }
    }

    private void performScroll(double scrollDelta) {
        if (nearbyItems.size() <= 1) return;

        if (scrollDelta > 0) {
            selectedIndex--;
        } else {
            selectedIndex++;
        }

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

        if (tickCounter % 10 == 0) {
            updateInventoryCache(mc);
        }

        AABB area = mc.player.getBoundingBox().inflate(PICKUP_EXPAND_XZ, PICKUP_EXPAND_Y, PICKUP_EXPAND_XZ);
        List<ItemEntity> found = mc.level.getEntitiesOfClass(ItemEntity.class, area, entity ->
                entity.isAlive() && !entity.getItem().isEmpty()
        );

        // 核心过滤逻辑修改
        if (filterMode == FilterMode.RARE_ONLY) {
            found.removeIf(entity -> {
                ItemStack stack = entity.getItem();

                // 检查白名单 (现在支持完整 NBT)
                if (FilterWhitelist.INSTANCE.contains(stack)) return false;

                return stack.getRarity() == Rarity.COMMON
                        && !stack.isEnchanted()
                        && !Utils.shouldShowTooltip(stack);
            });
        }

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

        found.sort(stableComparator);
        nearbyItems = found;

        if (nearbyItems.isEmpty()) {
            selectedIndex = 0;
            targetScrollOffset = 0;
        } else {
            selectedIndex = Math.max(0, Math.min(selectedIndex, nearbyItems.size() - 1));
            updateScrollOffset();
        }

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
