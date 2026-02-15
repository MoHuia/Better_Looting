package com.mohuia.better_looting.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.*;

public class Utils {

    // [优化] 移除了 redundant 的 hasItemInInventory 方法，请统一使用 Core.INSTANCE.isItemInInventory()

    /**
     * 判断是否应该显示 Tooltip
     * [改进] 使用更通用的判断逻辑，支持模组装备
     */
    public static boolean shouldShowTooltip(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 1. 如果物品有耐久度 (通常是工具、武器、装备)，直接显示
        if (stack.getMaxDamage() > 0) return true;

        // 2. 如果物品被附魔了，也显示 (比如附魔书或特殊物品)
        if (stack.isEnchanted()) return true;

        // 3. 保留原版特定类型的检查作为兜底
        Item item = stack.getItem();
        return item instanceof ArmorItem ||
                item instanceof TieredItem ||
                item instanceof ProjectileWeaponItem ||
                item instanceof ShieldItem ||
                item instanceof ElytraItem ||
                item instanceof TridentItem;
    }

    /**
     * 获取物品稀有度颜色或自定义颜色
     */
    public static int getItemStackDisplayColor(ItemStack stack) {
        // 优先使用物品名称定义的颜色 (如 RPG 物品)
        TextColor textColor = stack.getHoverName().getStyle().getColor();
        if (textColor != null) return textColor.getValue() | 0xFF000000;

        // 其次使用稀有度颜色
        ChatFormatting formatting = stack.getRarity().color;
        if (formatting != null && formatting.getColor() != null) return formatting.getColor() | 0xFF000000;

        // 默认白色
        return 0xFFFFFFFF;
    }

    /**
     * 颜色透明度应用工具
     */
    public static int applyAlpha(int color, float alpha) {
        int prevAlpha = (color >>> 24);
        int newAlpha = (int) (prevAlpha * alpha);
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    // --- 缓动函数 ---

    public static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    public static float easeOutCubic(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    /**
     * 混合颜色与透明度 (强制替换 Alpha)
     */
    public static int colorWithAlpha(int baseColor, int alpha255) {
        return (baseColor & 0x00FFFFFF) | (alpha255 << 24);
    }
}
