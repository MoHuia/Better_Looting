package com.mohuia.better_looting.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.*;

/**
 * 客户端通用工具类
 * <p>
 * 提供物品属性检测、颜色处理（ARGB 位运算）以及用于 UI 动画的缓动函数（Easing Functions）。
 * 注意：关于背包物品的检测逻辑已统一迁移至 {@link Core#isItemInInventory(Item)} 以优化性能。
 * </p>
 *
 * @author Mohuia
 */
public class Utils {

    // =========================================
    //               物品属性检测
    // =========================================

    /**
     * 判断是否应该在 UI 中为该物品显示详细的 Tooltip（工具提示）。
     * <p>
     * 逻辑说明：主要用于过滤普通材料，仅对具有耐久度、附魔或特定类型（如武器、装备）的物品显示详细信息，
     * 以保持界面层级清晰。该逻辑具有很强的通用性，能自动兼容绝大多数模组添加的装备。
     * </p>
     *
     * @param stack 待检测的物品栈
     * @return true 如果该物品是装备、工具、被附魔或具有特殊属性
     */
    public static boolean shouldShowTooltip(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 1. 如果物品有耐久度 (通常是工具、武器、装备)，直接显示
        if (stack.getMaxDamage() > 0) return true;

        // 2. 如果物品被附魔了，也显示 (比如附魔书或带有特殊附魔的物品)
        if (stack.isEnchanted()) return true;

        // 3. 原版特定类型的检查兜底，涵盖盔甲、工具、远战武器、盾牌、鞘翅和三叉戟
        Item item = stack.getItem();
        return item instanceof ArmorItem ||
                item instanceof TieredItem ||
                item instanceof ProjectileWeaponItem ||
                item instanceof ShieldItem ||
                item instanceof ElytraItem ||
                item instanceof TridentItem;
    }

    /**
     * 获取物品的显示颜色（用于 UI 边框高亮或文本渲染）。
     * <p>
     * 优先级策略：自定义物品名称颜色 (如 RPG 模组物品) > 物品稀有度颜色 (原版 Rarity) > 默认白色。
     * </p>
     *
     * @param stack 目标物品栈
     * @return ARGB 格式的颜色值
     */
    public static int getItemStackDisplayColor(ItemStack stack) {
        // 优先使用物品名称自定义的颜色
        TextColor textColor = stack.getHoverName().getStyle().getColor();
        if (textColor != null) {
            // 原版颜色值为 RGB，需要通过位或运算 (|) 补充不透明的 Alpha 通道 (0xFF000000)
            return textColor.getValue() | 0xFF000000;
        }

        // 其次使用稀有度颜色 (如：史诗物品为紫色)
        ChatFormatting formatting = stack.getRarity().color;
        if (formatting != null && formatting.getColor() != null) {
            return formatting.getColor() | 0xFF000000;
        }

        // 默认返回纯白色 (ARGB)
        return 0xFFFFFFFF;
    }

    // =========================================
    //               颜色与 Alpha 处理
    // =========================================

    /**
     * 按比例缩放现有颜色的 Alpha (透明度) 通道。
     *
     * @param color 原始 ARGB 颜色
     * @param alpha 透明度乘数 (0.0f - 1.0f)
     * @return 修改透明度后的 ARGB 颜色
     */
    public static int applyAlpha(int color, float alpha) {
        // 无符号右移 24 位获取原始 Alpha 值 (0-255)
        int prevAlpha = (color >>> 24);
        // 计算新的 Alpha 值
        int newAlpha = (int) (prevAlpha * alpha);
        // 使用按位与 (&) 清除原 Alpha，再用按位或 (|) 写入新 Alpha
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    /**
     * 强制替换颜色的 Alpha (透明度) 通道。
     *
     * @param baseColor 原始 ARGB 颜色
     * @param alpha255  新的 Alpha 值 (0 - 255)
     * @return 替换透明度后的 ARGB 颜色
     */
    public static int colorWithAlpha(int baseColor, int alpha255) {
        return (baseColor & 0x00FFFFFF) | (alpha255 << 24);
    }

    // =========================================
    //               动画缓动函数 (Easing)
    // =========================================

    /**
     * Ease-Out-Back 缓动函数。
     * <p>
     * 产生一种“稍微超出目标值然后再回弹”的动画效果，非常适合用于 UI 面板的弹出动画，增加界面的 Q弹感。
     * </p>
     *
     * @param x 动画当前进度 (0.0 - 1.0)
     * @return 计算后的插值比例
     */
    public static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    /**
     * Ease-Out-Cubic 缓动函数。
     * <p>
     * 产生一种“快速开始，平滑减速到停止”的动画效果，适合用于列表的平滑滚动或选中项的移动。
     * </p>
     *
     * @param x 动画当前进度 (0.0 - 1.0)
     * @return 计算后的插值比例
     */
    public static float easeOutCubic(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }
}
