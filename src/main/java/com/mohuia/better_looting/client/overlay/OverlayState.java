package com.mohuia.better_looting.client.overlay;

import com.mohuia.better_looting.client.Constants;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.*;
import java.util.stream.Collectors;

public class OverlayState {
    public float currentScroll = 0f;
    public float popupProgress = 0f;

    // 动画状态追踪
    private final Map<Integer, Float> itemEntryAnimations = new HashMap<>();

    // 时间追踪 (纳秒)
    private long lastFrameTime = -1;
    private float deltaTime = 0f; //上一帧到这一帧经过的秒数

    /**
     * 每帧调用一次，统一更新所有动画状态
     */
    public void tick(boolean shouldShow, float targetScroll, int itemCount, float visibleRows) {
        long now = System.nanoTime();
        if (lastFrameTime == -1) {
            lastFrameTime = now;
        }

        // 计算 Delta Time (秒)，并限制最大值为 0.1s 防止卡顿后瞬移
        this.deltaTime = (float) ((now - lastFrameTime) / 1_000_000_000.0);
        this.deltaTime = Math.min(this.deltaTime, 0.1f);
        this.lastFrameTime = now;

        // 1. 弹窗显示/隐藏动画 (使用指数衰减，速度系数 10.0)
        float targetPopup = shouldShow ? 1.0f : 0.0f;
        this.popupProgress = damp(this.popupProgress, targetPopup, 10.0f, deltaTime);

        // 如果完全隐藏，清理缓存以节省内存
        if (!shouldShow && this.popupProgress < 0.001f) {
            this.popupProgress = 0f;
            this.itemEntryAnimations.clear();
        }

        // 2. 滚动条平滑动画 (速度系数 15.0，比弹窗稍快)
        float maxScroll = Math.max(0, itemCount - visibleRows);
        float clampedTarget = Mth.clamp(targetScroll, 0, maxScroll);

        // 如果差异极小，直接吸附，避免微小抖动
        if (Math.abs(this.currentScroll - clampedTarget) < 0.001f) {
            this.currentScroll = clampedTarget;
        } else {
            this.currentScroll = damp(this.currentScroll, clampedTarget, 15.0f, deltaTime);
        }
    }

    /**
     * 获取单个物品的进入动画进度 (0.0 -> 1.0)
     * 现在使用时间增量，确保动画时长固定为约 0.15~0.2秒
     */
    public float getItemEntryProgress(int entityId) {
        return itemEntryAnimations.compute(entityId, (k, v) -> {
            float val = (v == null) ? 0f : v;
            if (val >= 1.0f) return 1.0f;

            // 速度 6.0f 意味着从 0 到 1 大约需要 1/6 = 0.16秒
            float next = val + (6.0f * deltaTime);
            return Math.min(1.0f, next);
        });
    }

    public void cleanupAnimations(List<ItemEntity> currentItems) {
        Set<Integer> currentIds = currentItems.stream().map(ItemEntity::getId).collect(Collectors.toSet());
        itemEntryAnimations.keySet().retainAll(currentIds);
    }

    /**
     * 帧率独立的平滑阻尼函数 (Frame-Rate Independent Damping)
     * 公式: lerp(current, target, 1 - exp(-speed * dt))
     */
    private float damp(float current, float target, float speed, float dt) {
        return Mth.lerp(1.0f - (float)Math.exp(-speed * dt), current, target);
    }
}
