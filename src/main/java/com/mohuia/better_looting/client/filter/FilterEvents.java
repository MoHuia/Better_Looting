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

/**
 * 客户端事件处理器
 * <p>
 * 负责监听屏幕初始化、按键、鼠标交互以及渲染事件，
 * 用于集成过滤面板 (FilterPanel) 到现有的容器屏幕 (AbstractContainerScreen) 中。
 * </p>
 *
 * @author Mohuia
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class FilterEvents {

    private static long lastClickTime = 0;
    /** 点击冷却时间 (毫秒)，防止极速连点导致的逻辑错误 */
    private static final long CLICK_COOLDOWN_MS = 150;

    /**
     * 在屏幕初始化后触发。
     * 用于重置面板状态，确保每次打开新 GUI 时面板默认为关闭。
     *
     * @param event 屏幕初始化事件
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        lastClickTime = 0;
        FilterPanel.close();
    }

    /**
     * 处理键盘按键事件。
     * 监听 Left ALT 键以切换过滤面板的显示状态。
     */
    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        // 仅在容器屏幕(如箱子、背包)中生效
        if (!(event.getScreen() instanceof AbstractContainerScreen)) return;

        if (event.getKeyCode() == GLFW.GLFW_KEY_LEFT_ALT) {
            FilterPanel.toggle();
        }
    }

    /**
     * 处理屏幕后处理渲染事件。
     * 在容器 GUI 绘制完成后，在其顶层绘制过滤面板。
     */
    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            FilterPanel.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), containerScreen);
        }
    }

    /**
     * 处理鼠标点击事件 (高优先级)。
     * <p>
     * 逻辑流程：
     * 1. 如果点击了过滤面板区域 -> 拦截事件，执行面板逻辑。
     * 2. 如果面板开启且点击了槽位/JEI -> 将物品加入/移除白名单，并拦截原版交互。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        // 1. 优先处理面板内部点击 (清除按钮、滚动条、面板内物品)
        if (FilterPanel.click(event.getMouseX(), event.getMouseY(), containerScreen)) {
            event.setCanceled(true); // 阻止事件传递给原版 GUI
            return;
        }

        // 2. 处理面板开启状态下，对外部槽位(Slot)或 JEI 物品的点击
        if (FilterPanel.isOpen() && (event.getButton() == 0 || event.getButton() == 1)) {
            // 如果鼠标光标上粘着物品，不允许操作，防止误添加
            ItemStack carriedStack = Minecraft.getInstance().player.containerMenu.getCarried();
            if (!carriedStack.isEmpty()) {
                return;
            }

            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            ItemStack jeiStack = JeiCompat.getHoveredItem();

            boolean isTargetingSlot = (hoveredSlot != null && hoveredSlot.hasItem() && hoveredSlot.isActive());
            boolean isTargetingJei = (!jeiStack.isEmpty());

            if (isTargetingSlot || isTargetingJei) {
                // 执行冷却检查
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < CLICK_COOLDOWN_MS) {
                    event.setCanceled(true);
                    return;
                }
                lastClickTime = currentTime;

                // 执行添加/移除逻辑
                ItemStack targetStack = isTargetingSlot ? hoveredSlot.getItem() : jeiStack;
                handleFilterAction(event.getButton(), targetStack);

                // 播放音效并拦截原版点击（防止把物品拿起来）
                float pitch = event.getButton() == 0 ? 1.0f : 0.5f;
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), pitch, 1.0f);
                event.setCanceled(true);
            }
        }
    }

    /**
     * 拦截鼠标拖拽事件。
     * 当面板开启时，阻止在原版 GUI 中的拖拽操作，防止误操作库存。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        if (FilterPanel.isOpen()) {
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) return;
            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            if (hoveredSlot != null) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 拦截鼠标释放事件。
     * 配合点击和拖拽拦截，确保完整的鼠标操作周期被接管。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        if (FilterPanel.isOpen()) {
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) return;
            Slot hoveredSlot = getHoveredSlot(containerScreen, event.getMouseX(), event.getMouseY());
            if (hoveredSlot != null && hoveredSlot.isActive()) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 处理鼠标滚轮事件。
     * 当面板开启时，优先滚动面板的列表。
     */
    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            if (FilterPanel.isOpen()) {
                // 如果面板消耗了滚动事件，则拦截原版滚动
                if (FilterPanel.scroll(event.getScrollDelta())) {
                    event.setCanceled(true);
                }
            }
        }
    }

    /**
     * 执行过滤白名单的修改逻辑
     * @param button 鼠标按键 (0: 左键添加, 其他: 右键移除)
     * @param stack 目标物品栈
     */
    private static void handleFilterAction(int button, ItemStack stack) {
        if (stack.isEmpty()) return;

        if (button == 0) {
            // 左键：添加到白名单
            if (!FilterWhitelist.INSTANCE.contains(stack)) {
                FilterWhitelist.INSTANCE.add(stack);
            }
        } else {
            // 右键：从白名单移除
            if (FilterWhitelist.INSTANCE.contains(stack)) {
                FilterWhitelist.INSTANCE.remove(stack);
            }
        }
    }

    /**
     * 辅助方法：获取当前鼠标下的容器槽位
     */
    private static Slot getHoveredSlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            // 计算鼠标相对于 GUI 左上角的坐标进行碰撞检测
            double relX = mouseX - guiLeft;
            double relY = mouseY - guiTop;
            if (relX >= (slot.x - 1) && relX < (slot.x + 16 + 1) &&
                    relY >= (slot.y - 1) && relY < (slot.y + 16 + 1)) {
                return slot;
            }
        }
        return null;
    }
}
