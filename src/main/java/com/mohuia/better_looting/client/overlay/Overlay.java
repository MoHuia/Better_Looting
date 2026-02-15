package com.mohuia.better_looting.client.overlay;

import com.mohuia.better_looting.client.Constants;
import com.mohuia.better_looting.client.Core;
import com.mohuia.better_looting.client.KeyInit;
import com.mohuia.better_looting.client.Utils;
import com.mohuia.better_looting.config.Config;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Overlay {

    private final OverlayState state = new OverlayState();
    private OverlayRenderer renderer;

    // 用于 KEY_TOGGLE 模式的开关状态
    private boolean isOverlayToggled = false;

    public Overlay() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 处理客户端 Tick 事件
     * 用于检测 KEY_TOGGLE 模式下的按键点击，确保单次点击只触发一次切换
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var mode = Config.CLIENT.activationMode.get();
        if (mode == Config.ActivationMode.KEY_TOGGLE) {
            // 消耗点击事件，防止每帧触发
            while (KeyInit.SHOW_OVERLAY.consumeClick()) {
                isOverlayToggled = !isOverlayToggled;
            }
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        var mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        var core = Core.INSTANCE;
        var nearbyItems = core.getNearbyItems();

        if (nearbyItems == null) return;
        if (this.renderer == null) this.renderer = new OverlayRenderer(mc);

        // --- 1. 状态更新 (含角度/移动/按键检查逻辑) ---

        var mode = Config.CLIENT.activationMode.get();
        double threshold = Config.CLIENT.lookDownAngle.get();
        boolean conditionMet = true; // 默认为 ALWAYS 模式的 true

        if (mode == Config.ActivationMode.LOOK_DOWN) {
            // 模式: 低头
            if (mc.player != null) {
                conditionMet = mc.player.getXRot() > threshold;
            }
        } else if (mode == Config.ActivationMode.STAND_STILL) {
            // 模式: 静止
            if (mc.player != null) {
                double dx = mc.player.getX() - mc.player.xo;
                double dz = mc.player.getZ() - mc.player.zo;
                double speedSqr = dx * dx + dz * dz;
                conditionMet = speedSqr < 0.0001;
            }
        } else if (mode == Config.ActivationMode.KEY_HOLD) {
            // 模式: 按住显示
            // 只有当按键已绑定且被按下时才显示
            conditionMet = !KeyInit.SHOW_OVERLAY.isUnbound() && KeyInit.SHOW_OVERLAY.isDown();
        } else if (mode == Config.ActivationMode.KEY_TOGGLE) {
            // 模式: 按键切换
            conditionMet = isOverlayToggled;
        }

        // 综合判断：
        // 1. 附近有物品
        // 2. 没开启自动模式 (自动模式通常不需要 HUD)
        // 3. 满足激活条件
        boolean shouldShow = !nearbyItems.isEmpty()
                && !core.isAutoMode()
                && conditionMet;

        // 1.1 预先构建 Layout
        var layout = new OverlayLayout(mc, state.popupProgress);

        // 1.2 执行时间步进
        state.tick(shouldShow, core.getTargetScrollOffset(), nearbyItems.size(), layout.visibleRows);

        // 1.3 性能剔除
        if (state.popupProgress < 0.001f) return;

        // 1.4 清理缓存
        if (mc.level != null && mc.level.getGameTime() % 20 == 0) {
            state.cleanupAnimations(nearbyItems);
        }

        // --- 2. 渲染 ---

        var gui = event.getGuiGraphics();
        var pose = gui.pose();

        pose.pushPose();
        pose.translate(layout.baseX + layout.slideOffset, layout.baseY, 0);
        pose.scale(layout.finalScale, layout.finalScale, 1.0f);

        if (state.popupProgress > 0.1f) drawHeader(gui, layout);

        int startIdx = Mth.floor(state.currentScroll);
        int endIdx = Mth.ceil(state.currentScroll + layout.visibleRows);
        boolean renderPrompt = false;
        float selectedBgAlpha = 0f;

        layout.applyStrictScissor();

        for (int i = 0; i < nearbyItems.size(); i++) {
            if (i < startIdx - 1 || i > endIdx + 1) continue;

            ItemEntity entity = nearbyItems.get(i);
            boolean isSelected = (i == core.getSelectedIndex());

            float entryProgress = state.getItemEntryProgress(entity.getId());
            float entryOffset = (1.0f - Utils.easeOutCubic(entryProgress)) * 50.0f;
            float itemAlpha = calculateAlpha(i - state.currentScroll, layout.visibleRows);

            if (itemAlpha * state.popupProgress <= 0.05f) continue;

            pose.pushPose();
            pose.translate(entryOffset, 0, 0);

            float finalBgAlpha = itemAlpha * state.popupProgress * layout.globalAlpha;
            float finalTextAlpha = itemAlpha * state.popupProgress;

            int y = layout.startY + (int) ((i - state.currentScroll) * layout.itemHeightTotal);

            renderer.renderItemRow(gui, Constants.LIST_X, y, layout.panelWidth, entity.getItem(), isSelected, finalBgAlpha, finalTextAlpha, !core.isItemInInventory(entity.getItem().getItem()));

            if (isSelected) {
                renderPrompt = true;
                selectedBgAlpha = finalBgAlpha;
            }
            pose.popPose();
        }

        if (renderPrompt) {
            layout.applyLooseScissor();
            renderer.renderKeyPrompt(gui, Constants.LIST_X, layout.startY, layout.itemHeightTotal, core.getSelectedIndex(), state.currentScroll, layout.visibleRows, selectedBgAlpha);
        }

        RenderSystem.disableScissor();

        if (nearbyItems.size() > layout.visibleRows) {
            int totalVisualH = (int) (layout.visibleRows * layout.itemHeightTotal);
            renderer.renderScrollBar(gui, nearbyItems.size(), layout.visibleRows, Constants.LIST_X - 6, layout.startY, totalVisualH, state.popupProgress * layout.globalAlpha, state.currentScroll);
        }

        pose.popPose();

        if (state.popupProgress > 0.9f && !nearbyItems.isEmpty()) {
            int sel = core.getSelectedIndex();
            if (sel >= 0 && sel < nearbyItems.size()) {
                var stack = nearbyItems.get(sel).getItem();
                if (Utils.shouldShowTooltip(stack)) {
                    renderer.renderTooltip(gui, stack, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), layout, state.currentScroll, sel);
                }
            }
        }
    }

    private void drawHeader(GuiGraphics gui, OverlayLayout layout) {
        int headerY = layout.startY - 14;
        int titleAlpha = (int)(state.popupProgress * layout.globalAlpha * 255);

        var pose = gui.pose();
        pose.pushPose();
        pose.translate(Constants.LIST_X, headerY, 0);
        pose.scale(0.75f, 0.75f, 1.0f);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.drawString(Minecraft.getInstance().font, "LOOT DETECTED", 0, 0, Utils.colorWithAlpha(0xFFFFD700, titleAlpha), true);
        pose.popPose();

        int lineColor = Utils.colorWithAlpha(0xFFAAAAAA, (int)(titleAlpha * 0.5));
        gui.fill(Constants.LIST_X, headerY + 10, Constants.LIST_X + layout.panelWidth, headerY + 11, lineColor);

        renderer.renderFilterTabs(gui, Constants.LIST_X + layout.panelWidth - 20, headerY + 10);
    }

    private float calculateAlpha(float relativeIndex, float visibleRows) {
        if (relativeIndex < 0) return Mth.clamp(1.0f + (relativeIndex * 1.5f), 0f, 1f);
        if (relativeIndex > visibleRows - 1.0f) return Mth.clamp(1.0f - (relativeIndex - (visibleRows - 1.0f)), 0f, 1f);
        return 1.0f;
    }
}
