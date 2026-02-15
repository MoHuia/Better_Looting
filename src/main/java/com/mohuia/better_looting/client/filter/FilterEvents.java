package com.mohuia.better_looting.client.filter;

import com.mohuia.better_looting.client.jei.JeiCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class FilterEvents {

    // --- 防抖动配置 ---
    private static long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 150; // 冷却时间 150ms，约等于 3 ticks

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // 重置状态
        lastClickTime = 0;
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen)) return;
        if (event.getKeyCode() == GLFW.GLFW_KEY_LEFT_ALT) {
            FilterPanel.toggle();
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            FilterPanel.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), containerScreen);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        // 1. 面板自身的点击 (删除) - 始终处理
        if (FilterPanel.click(event.getMouseX(), event.getMouseY(), containerScreen)) {
            event.setCanceled(true);
            return;
        }

        // 仅在面板打开且是左/右键时处理
        if (FilterPanel.isOpen() && (event.getButton() == 0 || event.getButton() == 1)) {

            // 获取当前鼠标上的物品
            ItemStack carriedStack = Minecraft.getInstance().player.containerMenu.getCarried();

            // === 状态检查 ===
            // 如果鼠标上已经拿了东西，我们完全放行，允许玩家把它放回去
            if (!carriedStack.isEmpty()) {
                return;
            }

            // 获取悬停的槽位或 JEI 物品
            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            ItemStack jeiStack = JeiCompat.getHoveredItem();

            // 判断是否点击了有效目标 (槽位且有物品，或者 JEI 物品)
            boolean isTargetingSlot = (hoveredSlot != null && hoveredSlot.hasItem() && hoveredSlot.isActive());
            boolean isTargetingJei = (!jeiStack.isEmpty());

            if (isTargetingSlot || isTargetingJei) {

                // === 防抖动逻辑 (核心修复) ===
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < CLICK_COOLDOWN_MS) {
                    // 点击太快了！直接取消事件，不执行逻辑，也不让原版处理。
                    // 这能有效防止 "双击" 触发原版的拾取机制。
                    event.setCanceled(true);
                    return;
                }

                // 更新最后点击时间
                lastClickTime = currentTime;

                // === 执行业务逻辑 ===
                ItemStack targetStack = isTargetingSlot ? hoveredSlot.getItem() : jeiStack;
                handleFilterAction(event.getButton(), targetStack);

                // 播放音效
                float pitch = event.getButton() == 0 ? 1.0f : 0.5f;
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), pitch, 1.0f);

                // 拦截事件，防止原版拿取
                event.setCanceled(true);
            }
        }
    }

    /**
     * 强力拦截拖拽：防止点击瞬间产生的微小位移被判定为拖拽
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        if (FilterPanel.isOpen()) {
            // 如果鼠标拿着东西，允许拖拽 (原版分堆等操作)
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) {
                return;
            }

            // 如果鼠标是空的，禁止任何在槽位上的拖拽判定
            // 这通常是 "点击太快变成拿取" 的罪魁祸首
            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            if (hoveredSlot != null) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 拦截释放：防止点击的 "后半段" 触发原版逻辑
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        if (FilterPanel.isOpen()) {
            // 同样，如果拿着东西，放行
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) {
                return;
            }

            // 如果鼠标空的，拦截槽位上的释放事件
            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            if (hoveredSlot != null && hoveredSlot.isActive()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            if (FilterPanel.isOpen()) {
                if (FilterPanel.scroll(event.getScrollDelta())) {
                    event.setCanceled(true);
                }
            }
        }
    }

    // --- 辅助方法 ---

    private static void handleFilterAction(int button, ItemStack stack) {
        if (stack.isEmpty()) return;

        if (button == 0) { // 左键添加
            if (!FilterWhitelist.INSTANCE.contains(stack.getItem())) {
                FilterWhitelist.INSTANCE.add(stack.getItem());
            }
        } else { // 右键移除
            if (FilterWhitelist.INSTANCE.contains(stack.getItem())) {
                FilterWhitelist.INSTANCE.remove(stack.getItem());
            }
        }
    }

    private static Slot getHoveredSlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;

            // 转换为相对坐标计算，更贴合原版的处理方式
            double relX = mouseX - guiLeft;
            double relY = mouseY - guiTop;

            // 【核心修复】加上原版自带的 1 像素宽容度 (Margin)，判定区域变为 18x18
            if (relX >= (slot.x - 1) && relX < (slot.x + 16 + 1) &&
                    relY >= (slot.y - 1) && relY < (slot.y + 16 + 1)) {
                return slot;
            }
        }
        return null;
    }
}
