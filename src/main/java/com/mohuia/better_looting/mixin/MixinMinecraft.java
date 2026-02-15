package com.mohuia.better_looting.mixin;

import com.mohuia.better_looting.client.Core;
import com.mohuia.better_looting.client.KeyInit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow @Final public Options options;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void betterLooting$interceptKeybinds(CallbackInfo ci) {
        // 只有当周围有可拾取物品时才进行拦截
        if (Core.shouldIntercept()) {

            // 消耗掉按键点击事件，防止它传递给原版逻辑。
            // 逻辑由 Core.tick() 中的 isDown() 状态检测接管。
            while (KeyInit.PICKUP.consumeClick()) {
                // Do nothing, just eat the input.
            }

            // --- 冲突处理 ---
            // 检查副手交换键，如果它和 PICKUP 是同一个键，也消耗掉它的点击。
            KeyMapping swapKey = this.options.keySwapOffhand;
            if (swapKey.same(KeyInit.PICKUP)) {
                while (swapKey.consumeClick()) {
                    // Eat it so offhand swap doesn't happen
                }
            }
        }
    }
}
