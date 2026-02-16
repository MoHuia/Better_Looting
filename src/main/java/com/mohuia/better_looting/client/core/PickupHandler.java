package com.mohuia.better_looting.client.core;

import net.minecraft.util.Mth;

/**
 * 拾取逻辑处理器.
 * <p>
 * 实现了严格的 点击/长按 分离逻辑：
 * 1. 短按松开 -> 单次拾取 (Tap)
 * 2. 长按到底 -> 批量拾取 (Hold)
 * 3. 长按中途松开 -> 取消操作 (Cancel)
 */
public class PickupHandler {

    // --- 配置常量 ---

    /** * 触发批量拾取所需的总时长 (tick).
     * 原为 20 (1秒)，现改为 12 (0.6秒)，手感更快捷。
     */
    private static final int MAX_HOLD_TICKS = 12;

    /**
     * 点击判定阈值 (tick).
     * - 按住时间 < 此值：视为 "点击"，松开时触发单次拾取。
     * - 按住时间 >= 此值：视为 "长按"，UI显示进度，松开时视为 "取消"。
     * 保持 4 tick (0.2秒)，既能保证点击响应快，又能区分长按。
     */
    private static final int PRESS_THRESHOLD_TICKS = 4;

    /** 自动拾取冷却时间 (tick) */
    private static final int AUTO_COOLDOWN_MAX = 10;

    // --- 内部状态 ---
    private int ticksHeld = 0;
    private int autoPickupCooldown = 0;
    private boolean wasKeyDown = false;

    // 防止一次长按触发多次批量拾取
    private boolean batchPickupTriggered = false;

    public enum PickupAction {
        NONE,
        SINGLE, // 单次拾取 (点击触发)
        BATCH   // 批量拾取 (长按触发)
    }

    /**
     * 每帧调用的输入处理逻辑
     */
    public PickupAction tickInput(boolean isKeyDown, boolean isShiftDown, boolean hasTargets) {
        PickupAction action = PickupAction.NONE;

        // 处理自动拾取冷却
        if (autoPickupCooldown > 0) autoPickupCooldown--;

        if (isKeyDown) {
            // ===========================
            //       按下状态 (Holding)
            // ===========================
            if (!wasKeyDown) {
                // 1. 刚刚按下 (Just Pressed)
                // 重置计时器
                ticksHeld = 0;
                batchPickupTriggered = false;
            } else {
                // 2. 持续按住
                if (hasTargets && !batchPickupTriggered) {
                    ticksHeld++;

                    // 只有按满时间才触发 BATCH
                    if (ticksHeld >= MAX_HOLD_TICKS) {
                        action = PickupAction.BATCH;
                        batchPickupTriggered = true; // 上锁
                    }
                }
            }
        } else {
            // ===========================
            //       松开状态 (Released)
            // ===========================
            if (wasKeyDown) {
                // 刚刚松开的一瞬间 (Just Released)

                // 如果长按动作(Batch)没有触发过，说明是中途松开
                if (!batchPickupTriggered && hasTargets) {

                    // 核心逻辑：判断时长
                    if (ticksHeld < PRESS_THRESHOLD_TICKS) {
                        // 时间很短 -> 判定为 "点击" -> 触发单次拾取
                        action = PickupAction.SINGLE;
                    }
                    // else {
                    //    时间较长 (>= 阈值) -> 判定为 "长按中途取消" -> 什么都不做 (NONE)
                    // }
                }
            }

            // 重置状态
            ticksHeld = 0;
            batchPickupTriggered = false;
        }

        wasKeyDown = isKeyDown;
        return action;
    }

    /**
     * 获取用于 UI 渲染的进度 (0.0f ~ 1.0f).
     */
    public float getProgress() {
        // 1. 如果还在点击判定期内，不显示进度条 (避免点击时UI闪烁)
        if (ticksHeld < PRESS_THRESHOLD_TICKS) {
            return 0.0f;
        }

        // 2. 如果已经触发了批量拾取，保持满圈
        if (batchPickupTriggered) {
            return 1.0f;
        }

        // 3. 计算长按进度
        // 将区间 [Threshold, Max] 映射到 [0.0, 1.0]
        float effectiveTicks = ticksHeld - PRESS_THRESHOLD_TICKS;
        float effectiveMax = MAX_HOLD_TICKS - PRESS_THRESHOLD_TICKS;

        // 这里的 effectiveMax 现在是 12 - 4 = 8 tick (0.4秒)
        // 意味着进度条会非常快速地填满

        return Mth.clamp(effectiveTicks / effectiveMax, 0.0f, 1.0f);
    }

    // --- 自动拾取相关 (保持不变) ---

    public boolean canAutoPickup() { return autoPickupCooldown <= 0; }
    public void resetAutoCooldown() { this.autoPickupCooldown = AUTO_COOLDOWN_MAX; }

    // 是否正在交互中 (长按超过阈值才算交互，短按不算)
    public boolean isInteracting() { return ticksHeld >= PRESS_THRESHOLD_TICKS; }
}
