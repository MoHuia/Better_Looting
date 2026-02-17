package com.mohuia.better_looting.client.overlay;

import com.mohuia.better_looting.client.*;
import com.mohuia.better_looting.client.core.VisualItemEntry;
import com.mohuia.better_looting.config.Config;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * HUD 覆盖层主控制器.
 * <p>
 * 负责协调 HUD 的显示逻辑、生命周期管理以及渲染调度。
 * <br>充当 MVC 架构中的 "View Controller"，连接 {@link OverlayState} (Model) 和 {@link OverlayRenderer} (View)。
 * </p>
 */
public class Overlay {

    private final OverlayState state = new OverlayState();
    private OverlayRenderer renderer;

    /**
     * 覆盖层开关状态.
     * <p>仅在 {@link Config.ActivationMode#KEY_TOGGLE} 模式下有效，用于记录当前的显示/隐藏状态。</p>
     */
    private boolean isOverlayToggled = false;

    public Overlay() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 客户端逻辑 Tick 处理.
     * <p>主要用于处理按键的 "Toggle" (切换) 逻辑。
     * 必须在 ClientTick 中处理而非 Render 中，以确保 {@code consumeClick()} 能够正确响应单次按下。</p>
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (Config.CLIENT.activationMode.get() == Config.ActivationMode.KEY_TOGGLE) {
            while (KeyInit.SHOW_OVERLAY.consumeClick()) {
                isOverlayToggled = !isOverlayToggled;
            }
        }
    }

    /**
     * HUD 渲染事件入口.
     * <p>所有的绘制逻辑由此开始，每帧调用。</p>
     *
     * @param event 渲染事件，提供 {@link GuiGraphics} 上下文
     */
    @SubscribeEvent
    public void onRenderGui(RenderGuiOverlayEvent.Post event) {
        // 确保渲染层级正确：仅在原版快捷栏渲染后绘制
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; // 打开 GUI (如背包/聊天框) 时不显示

        Core core = Core.INSTANCE;
        var nearbyItems = core.getNearbyItems();
        if (nearbyItems == null) return;

        // 延迟初始化渲染器
        if (this.renderer == null) this.renderer = new OverlayRenderer(mc);

        // =========================================
        //           1. 状态判断与更新
        // =========================================

        boolean conditionMet = checkActivationCondition(mc);

        // 综合显示条件：列表非空 && 非自动模式 && 满足激活条件(按键/视线等)
        boolean shouldShow = !nearbyItems.isEmpty() && !core.isAutoMode() && conditionMet;

        // 创建布局对象 (Immediate Mode，每帧重新计算布局参数)
        OverlayLayout layout = new OverlayLayout(mc, state.popupProgress);

        // 更新动画状态机 (弹窗弹出进度、滚动位置平滑插值)
        state.tick(shouldShow, core.getTargetScrollOffset(), nearbyItems.size(), layout.visibleRows);

        // 性能优化：如果完全透明且无需显示，直接跳过后续昂贵的渲染操作
        if (state.popupProgress < 0.001f) return;

        // 定期清理未使用的动画缓存 (每秒一次，防止内存泄漏)
        if (mc.level != null && mc.level.getGameTime() % 20 == 0) {
            state.cleanupAnimations(nearbyItems);
        }

        // =========================================
        //           2. 渲染流程
        // =========================================

        GuiGraphics gui = event.getGuiGraphics();
        var pose = gui.pose();

        pose.pushPose();

        // 2.0 应用全局变换
        // 包含：滑入滑出位移 (Slide Offset) 和 整体缩放 (Scale)
        pose.translate(layout.baseX + layout.slideOffset, layout.baseY, 0);
        pose.scale(layout.finalScale, layout.finalScale, 1.0f);

        // 2.1 绘制标题栏
        if (state.popupProgress > 0.1f) {
            drawHeader(gui, layout);
        }

        // 2.2 绘制物品列表 (核心循环)
        // 计算渲染范围 (Culling)，只渲染可见区域内的物品，提高性能
        int startIdx = Mth.floor(state.currentScroll);
        int endIdx = Mth.ceil(state.currentScroll + layout.visibleRows);

        boolean renderPrompt = false;
        float selectedBgAlpha = 0f;

        // 开启严格裁剪 (Scissor Test)，防止物品渲染溢出列表框背景
        layout.applyStrictScissor();

        for (int i = 0; i < nearbyItems.size(); i++) {
            // 视锥剔除：跳过视口外的行
            if (i < startIdx - 1 || i > endIdx + 1) continue;

            VisualItemEntry entry = nearbyItems.get(i);
            boolean isSelected = (i == core.getSelectedIndex());

            // 动画计算：
            // 1. itemEntryProgress: 单个物品的独立进入动画
            // 2. entryOffset: 根据进度计算 X 轴位移 (从右侧滑入效果)
            float entryProgress = state.getItemEntryProgress(entry.getPrimaryId());
            float entryOffset = (1.0f - Utils.easeOutCubic(entryProgress)) * 50.0f;

            // 透明度计算：列表顶部和底部的淡出效果
            float itemAlpha = calculateListEdgeAlpha(i - state.currentScroll, layout.visibleRows);

            // 如果该行基本不可见，跳过绘制
            if (itemAlpha * state.popupProgress <= 0.05f) continue;

            pose.pushPose();
            pose.translate(entryOffset, 0, 0);

            // 最终合成透明度
            float finalBgAlpha = itemAlpha * state.popupProgress * layout.globalAlpha;
            float finalTextAlpha = itemAlpha * state.popupProgress;

            // 计算相对 Y 坐标
            int y = layout.startY + (int) ((i - state.currentScroll) * layout.itemHeightTotal);

            // 执行绘制
            renderer.renderItemRow(gui, Constants.LIST_X, y, layout.panelWidth, entry,
                    isSelected, finalBgAlpha, finalTextAlpha, !core.isItemInInventory(entry.getItem().getItem()));

            if (isSelected) {
                renderPrompt = true;
                selectedBgAlpha = finalBgAlpha;
            }
            pose.popPose();
        }

        // 2.3 绘制交互提示 (如 [F] 拾取)
        // 使用宽松裁剪 (允许进度条光晕稍微超出一点格子)
        if (renderPrompt) {
            layout.applyLooseScissor();
            renderer.renderKeyPrompt(gui, Constants.LIST_X, layout.startY, layout.itemHeightTotal,
                    core.getSelectedIndex(), state.currentScroll, layout.visibleRows, selectedBgAlpha);
        }

        // 关闭裁剪
        RenderSystem.disableScissor();

        // 2.4 绘制滚动条
        if (nearbyItems.size() > layout.visibleRows) {
            int totalVisualH = (int) (layout.visibleRows * layout.itemHeightTotal);
            renderer.renderScrollBar(gui, nearbyItems.size(), layout.visibleRows,
                    Constants.LIST_X - 6, layout.startY, totalVisualH,
                    state.popupProgress * layout.globalAlpha, state.currentScroll);
        }

        // 恢复全局变换
        pose.popPose();

        // 2.5 绘制 Tooltip (悬浮提示)
        // 注意：Tooltip 必须在 popPose 之后绘制，以免受到列表缩放或裁剪的影响，导致文字模糊或被截断。
        if (state.popupProgress > 0.9f && !nearbyItems.isEmpty()) {
            int sel = core.getSelectedIndex();
            if (sel >= 0 && sel < nearbyItems.size()) {
                var stack = nearbyItems.get(sel).getItem();
                if (Utils.shouldShowTooltip(stack)) {
                    renderer.renderTooltip(gui, stack, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                            layout, state.currentScroll, sel);
                }
            }
        }
    }

    /**
     * 检查是否满足 Config 中定义的激活模式.
     * @return 如果当前状态应该显示 HUD，则返回 true
     */
    private boolean checkActivationCondition(Minecraft mc) {
        var mode = Config.CLIENT.activationMode.get();
        switch (mode) {
            case LOOK_DOWN:
                // 玩家低头角度超过阈值
                return mc.player != null && mc.player.getXRot() > Config.CLIENT.lookDownAngle.get();
            case STAND_STILL:
                // 玩家位置基本静止
                if (mc.player == null) return false;
                double dx = mc.player.getX() - mc.player.xo;
                double dz = mc.player.getZ() - mc.player.zo;
                return (dx * dx + dz * dz) < 0.0001;
            case KEY_HOLD:
                // 按住指定按键
                return !KeyInit.SHOW_OVERLAY.isUnbound() && KeyInit.SHOW_OVERLAY.isDown();
            case KEY_TOGGLE:
                // 按键切换模式 (由 onClientTick 维护状态)
                return isOverlayToggled;
            case ALWAYS:
            default:
                return true;
        }
    }

    /**
     * 绘制列表上方的标题栏和过滤标签.
     */
    private void drawHeader(GuiGraphics gui, OverlayLayout layout) {
        int headerY = layout.startY - 14;
        int titleAlpha = (int)(state.popupProgress * layout.globalAlpha * 255);

        var pose = gui.pose();
        pose.pushPose();
        pose.translate(Constants.LIST_X, headerY, 0);
        // 标题文字缩小显示 (0.75x)
        pose.scale(0.75f, 0.75f, 1.0f);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        gui.drawString(Minecraft.getInstance().font, "LOOT DETECTED", 0, 0, Utils.colorWithAlpha(0xFFFFD700, titleAlpha), true);
        pose.popPose();

        // 绘制分割线
        int lineColor = Utils.colorWithAlpha(0xFFAAAAAA, (int)(titleAlpha * 0.5));
        gui.fill(Constants.LIST_X, headerY + 10, Constants.LIST_X + layout.panelWidth, headerY + 11, lineColor);

        // 绘制过滤模式指示器 (Tab)
        renderer.renderFilterTabs(gui, Constants.LIST_X + layout.panelWidth - 20, headerY + 10);
    }

    /**
     * 计算列表顶部和底部的边缘淡出 Alpha 值.
     * <p>用于实现列表滚动时的边缘渐变消失效果。</p>
     *
     * @param relativeIndex 物品相对于当前滚动位置的索引差值 (itemIndex - currentScroll)
     * @param visibleRows 可见行数
     * @return 透明度系数 (0.0f ~ 1.0f)
     */
    private float calculateListEdgeAlpha(float relativeIndex, float visibleRows) {
        // 顶部边缘淡出：索引小于 0 的部分逐渐透明
        if (relativeIndex < 0) return Mth.clamp(1.0f + (relativeIndex * 1.5f), 0f, 1f);

        // 底部边缘淡出：索引超过可见区域的部分逐渐透明
        if (relativeIndex > visibleRows - 1.0f) {
            return Mth.clamp(1.0f - (relativeIndex - (visibleRows - 1.0f)), 0f, 1f);
        }

        // 中间区域完全不透明
        return 1.0f;
    }
}
