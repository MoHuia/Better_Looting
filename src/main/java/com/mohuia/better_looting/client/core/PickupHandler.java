package com.mohuia.better_looting.client.core;

import net.minecraft.util.Mth;

/**
 * 拾取输入逻辑处理器.
 * 实现"点击"与"长按"的分离逻辑：
 * 点击 (Tap):短按并快速松开 -> 触发单次拾取 ({@link PickupAction#SINGLE})。
 * 长按 (Hold):按住超过阈值 -> 触发批量拾取 ({@link PickupAction#BATCH})。
 * 取消 (Cancel):长按未达到满进度便松开 -> 不执行任何操作。
 *
 */
public class PickupHandler {

    /** 触发批量拾取所需时长 (12 ticks ≈ 0.6秒) */
    private static final int MAX_HOLD_TICKS = 12;

    /** * 点击判定阈值 (4 ticks ≈ 0.2秒).
     * 按住时间小于此值视为点击，大于此值开始显示进度条。
     */
    private static final int PRESS_THRESHOLD_TICKS = 4;

    /** 自动拾取触发冷却 (10 ticks) */
    private static final int AUTO_COOLDOWN_MAX = 10;

    // --- 内部状态 ---

    private int ticksHeld = 0;
    private int autoPickupCooldown = 0;
    private boolean wasKeyDown = false;

    /** 防止单次长按重复触发批量拾取 */
    private boolean batchPickupTriggered = false;

    /** 拾取动作结果枚举 */
    public enum PickupAction {
        NONE,
        SINGLE,
        BATCH
    }

    /**
     * 处理输入信号并返回动作结果.
     * 应在 Client Tick 中每帧调用。
     *
     * @param isKeyDown 拾取键当前是否按下
     * @param isShiftDown Shift 键是否按下
     * @param hasTargets 当前是否有可拾取目标
     * @return 当前帧应该执行的动作
     */
    public PickupAction tickInput(boolean isKeyDown, boolean isShiftDown, boolean hasTargets) {
        PickupAction action = PickupAction.NONE;

        if (autoPickupCooldown > 0) autoPickupCooldown--;

        if (isKeyDown) {
            // --- 按下状态 ---
            if (!wasKeyDown) {
                // Just Pressed: 重置
                ticksHeld = 0;
                batchPickupTriggered = false;
            } else {
                // Holding: 累加时间
                if (hasTargets && !batchPickupTriggered) {
                    ticksHeld++;
                    // 达到长按阈值，触发批量
                    if (ticksHeld >= MAX_HOLD_TICKS) {
                        action = PickupAction.BATCH;
                        batchPickupTriggered = true;
                    }
                }
            }
        } else {
            // --- 松开状态 ---
            if (wasKeyDown) {
                // Just Released: 判定点击
                // 如果未触发过 Batch 且时间极短，视为 Tap
                if (!batchPickupTriggered && hasTargets) {
                    if (ticksHeld < PRESS_THRESHOLD_TICKS) {
                        action = PickupAction.SINGLE;
                    }
                }
            }
            // 重置
            ticksHeld = 0;
            batchPickupTriggered = false;
        }

        wasKeyDown = isKeyDown;
        return action;
    }

    /**
     * 获取 HUD 进度条 (0.0f - 1.0f).
     * 进度条仅在按住时间超过点击阈值后才开始显示。
     */
    public float getProgress() {
        // 避免点击时 UI 闪烁
        if (ticksHeld < PRESS_THRESHOLD_TICKS) return 0.0f;
        if (batchPickupTriggered) return 1.0f;

        float effectiveTicks = ticksHeld - PRESS_THRESHOLD_TICKS;
        float effectiveMax = MAX_HOLD_TICKS - PRESS_THRESHOLD_TICKS;

        return Mth.clamp(effectiveTicks / effectiveMax, 0.0f, 1.0f);
    }

    public boolean canAutoPickup() { return autoPickupCooldown <= 0; }
    public void resetAutoCooldown() { this.autoPickupCooldown = AUTO_COOLDOWN_MAX; }

    /** 是否正在进行交互 (长按中) */
    public boolean isInteracting() { return ticksHeld >= PRESS_THRESHOLD_TICKS; }
}
