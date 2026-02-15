package com.mohuia.better_looting.client.jei;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * JEI 兼容性桥接类 (软依赖包装器)
 * <p>
 * 该类用于安全地与 JEI 交互。如果未安装 JEI，调用此类的方法不会导致崩溃。
 * 利用了 Java 类加载机制：只有在访问内部类 {@link Internal} 时，
 * 才会尝试加载引用了 JEI API 的 {@link JeiPlugin} 类。
 * </p>
 *
 * @author Mohuia
 */
public class JeiCompat {
    /** 缓存 JEI 是否已加载的状态，避免重复查询 ModList */
    public static final boolean IS_JEI_LOADED = ModList.get().isLoaded("jei");

    /**
     * 获取鼠标下方的 JEI 物品（安全方法）
     *
     * @return 如果 JEI 加载且鼠标下方有物品，返回该物品栈；否则返回空物品栈。
     */
    public static ItemStack getHoveredItem() {
        if (IS_JEI_LOADED) {
            return Internal.getHoveredItem();
        }
        return ItemStack.EMPTY;
    }

    /**
     * 内部静态类
     * <p>
     * 只有当外部代码调用 {@link #getHoveredItem()} 且 {@code IS_JEI_LOADED} 为 true 时，
     * JVM 才会加载这个类。这意味着如果未安装 JEI，{@link JeiPlugin} 永远不会被加载，
     * 从而避免了 ClassNotFoundException。
     * </p>
     */
    private static class Internal {
        static ItemStack getHoveredItem() {
            // 这里引用了 JeiPlugin，它 import 了 JEI 的类
            return JeiPlugin.getIngredientUnderMouse();
        }
    }
}
