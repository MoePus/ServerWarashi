package com.moepus.serverwarashi;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.UUID;

public class TicketOwner<OwnerType> {
    private Object owner;
    private String name;
    private BlockPos pos;

    public TicketOwner(Ticket<OwnerType> ticket, ServerLevel level) {
        IPauseableTicket pauseable = (IPauseableTicket) (Object) ticket;
        OwnerType key = (OwnerType) pauseable.serverWarashi$getKey();

        this.name = ticket.getType().toString();

        if (key.getClass().getName().endsWith("ForcedChunkManager$TicketOwner")) {
            try {
                Field ownerField = key.getClass().getDeclaredField("owner");
                ownerField.setAccessible(true);
                owner = ownerField.get(key);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
            if (owner instanceof BlockPos blockPos) {
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState != null) {
                    this.name = blockState.getBlock().toString();
                } else {
                    this.name = "Unknown block";
                }
                this.pos = blockPos;
            } else if (owner instanceof UUID uuid) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    this.name = entity.getType().toString();
                    this.pos = entity.blockPosition();
                } else {
                    this.name = "Unknown entity";
                }
            }
        } else {
            owner = key;
            if (owner instanceof BlockPos blockPos) {
                this.pos = blockPos;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TicketOwner<?> that = (TicketOwner<?>) o;
        return Objects.equals(this.name, that.name)
                && Objects.equals(this.owner, that.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, owner);
    }

    private String getName() {
        return name;
    }

    @Override
    public String toString() {
        String position = (pos == null) ? "null" : String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
        return getName() + "@" + position;
    }

    public Component asComponent() {
        if (pos == null) {
            return Component.literal(getName() + "@null");
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        MutableComponent message = Component.empty();
        // 普通部分：名字 + @
        Component base = Component.literal(getName() + "@");

        // 坐标部分：金色 + 可点击
        Component coord = Component.literal("(" + x + "," + y + "," + z + ")")
                .setStyle(
                        Style.EMPTY
                                .withColor(TextColor.fromRgb(0xFFD700))
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        String.format("/tp @p %d %d %d", x, y, z)
                                ))
                );

        return message.append(base).append(coord);
    }
}
