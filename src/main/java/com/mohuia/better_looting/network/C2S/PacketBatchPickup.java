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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketBatchPickup {
    private final List<Integer> entityIds;
    private final boolean isAuto; // [新增] 标记是否为自动拾取

    // 手动调用时，默认为 false
    public PacketBatchPickup(List<Integer> entityIds) {
        this(entityIds, false);
    }

    public PacketBatchPickup(List<Integer> entityIds, boolean isAuto) {
        this.entityIds = entityIds;
        this.isAuto = isAuto;
    }

    public PacketBatchPickup(FriendlyByteBuf buf) {
        this.isAuto = buf.readBoolean(); // [新增] 先读取布尔值
        int count = buf.readVarInt();
        this.entityIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.entityIds.add(buf.readInt());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(isAuto); // [新增] 先写入布尔值
        buf.writeVarInt(entityIds.size());
        for (int id : entityIds) {
            buf.writeInt(id);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            boolean anySuccess = false;
            boolean anyFull = false;

            for (int entityId : entityIds) {
                Entity target = player.level().getEntity(entityId);

                if (target instanceof ItemEntity itemEntity && itemEntity.isAlive() && player.distanceToSqr(target) < 64.0) {

                    CompoundTag tag = new CompoundTag();
                    itemEntity.saveWithoutId(tag);
                    if (tag.getShort("PickupDelay") > 0) {
                        continue;
                    }

                    ItemStack stack = itemEntity.getItem().copy();
                    int originalCount = stack.getCount();

                    if (player.getInventory().add(stack)) {
                        anySuccess = true;
                        player.take(itemEntity, originalCount - stack.getCount());

                        if (stack.isEmpty()) {
                            itemEntity.discard();
                        } else {
                            itemEntity.getItem().setCount(stack.getCount());
                            anyFull = true;
                        }
                    } else {
                        anyFull = true;
                    }
                }
            }

            if (anySuccess) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                        ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
            }

            if (anyFull) {
                // [核心修改] 只有不是自动拾取时，才发送失败反馈
                // 这样开启自动模式时，如果背包满了，玩家不会受到噪音和红字刷屏的干扰
                if (!isAuto) {
                    if (!anySuccess) {
                        player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.2f);
                    }
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
