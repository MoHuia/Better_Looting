package com.mohuia.better_looting.network.C2S;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 单物品拾取数据包 (Client -> Server)。
 */
public class PacketPickupItem {
    private final int entityId;

    public PacketPickupItem(int entityId) {
        this.entityId = entityId;
    }

    public PacketPickupItem(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity target = player.level().getEntity(entityId);

            if (target instanceof ItemEntity itemEntity && itemEntity.isAlive() && player.distanceToSqr(target) < 64.0) {

                // 防止拾取冷却中的物品（如玩家刚丢弃的）
                CompoundTag tag = new CompoundTag();
                itemEntity.saveWithoutId(tag);
                if (tag.getShort("PickupDelay") > 0) {
                    return;
                }

                ItemStack stack = itemEntity.getItem().copy();
                int originalCount = stack.getCount();

                // 尝试添加到背包
                if (player.getInventory().add(stack)) {
                    // 同步动画：玩家"拿取"了物品
                    player.take(itemEntity, originalCount - stack.getCount());

                    // 播放音效
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                            ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);

                    if (stack.isEmpty()) {
                        itemEntity.discard();
                    } else {
                        // 背包满了，物品还剩一点
                        itemEntity.getItem().setCount(stack.getCount());
                        sendInventoryFullMessage(player);
                    }
                } else {
                    // 背包完全满了
                    player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.2f);
                    sendInventoryFullMessage(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void sendInventoryFullMessage(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.better_looting.inventory_full").withStyle(ChatFormatting.RED),
                true
        );
    }
}
