package com.mohuia.better_looting.client.core;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * 视觉物品条目.
 * 代表 UI 列表中的一行。该类负责将多个同类 {@link ItemEntity} 聚合显示。
 * 包含代表性物品栈 (Representative Stack) 和源实体列表。
 */
public class VisualItemEntry {
    private final List<ItemEntity> sourceEntities = new ArrayList<>();
    private final ItemStack representativeStack;
    private int totalCount = 0;

    /**
     * 游戏内实体构造函数.
     * @param firstEntity 发现的第一个实体
     */
    public VisualItemEntry(ItemEntity firstEntity) {
        this.sourceEntities.add(firstEntity);
        this.representativeStack = firstEntity.getItem().copy();
        this.totalCount = this.representativeStack.getCount();
    }

    /**
     * 仅用于 ConfigScreen 预览的构造函数.
     * @param stack 预览用的物品栈
     */
    public VisualItemEntry(ItemStack stack) {
        this.representativeStack = stack.copy();
        this.totalCount = stack.getCount();
    }

    /**
     * 尝试合并另一个实体到当前条目.
     *
     * @param entity 待合并的实体
     * @return true 如果合并成功 (类型相同且允许堆叠); false 否则
     */
    public boolean tryMerge(ItemEntity entity) {
        ItemStack otherStack = entity.getItem();

        // 1. 强制检查堆叠规则
        // 工具、武器、盔甲等 (maxStackSize=1) 即使完全相同也不合并，确保可以在列表中分开选择。
        if (!this.representativeStack.isStackable()) {
            return false;
        }

        // 2. 检查 Item 和 NBT 是否一致
        if (ItemStack.isSameItemSameTags(this.representativeStack, otherStack)) {
            this.sourceEntities.add(entity);
            this.totalCount += otherStack.getCount();
            return true;
        }

        return false;
    }

    public ItemStack getItem() { return representativeStack; }
    public int getCount() { return totalCount; }
    public List<ItemEntity> getSourceEntities() { return sourceEntities; }

    /**
     * 获取主 ID (用于排序稳定性).
     * @return 第一个源实体的 ID，如果列表为空返回 -1
     */
    public int getPrimaryId() {
        return sourceEntities.isEmpty() ? -1 : sourceEntities.get(0).getId();
    }
}
