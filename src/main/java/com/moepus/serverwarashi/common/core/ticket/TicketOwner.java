package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.common.report.ReportButtons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord")
public final class TicketOwner {
    private final TicketOwnerKey key;
    private final String name;
    private final BlockPos pos;

    public TicketOwner(TicketOwnerKey key, String name, BlockPos pos) {
        this.key = Objects.requireNonNull(key, "key");
        this.name = name;
        this.pos = pos;
    }

    @Override
    public boolean equals(Object o) {
        // 身份相等仅取决于 key（跨 tick 稳定）
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TicketOwner that = (TicketOwner) o;
        return Objects.equals(this.key, that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    public String getName() {
        return name;
    }

    public BlockPos representativePos() {
        return pos;
    }

    @Override
    public String toString() {
        // "名字@(x, y, z)"；无坐标时用 null
        String position = (pos == null) ? "null" : String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
        return getName() + "@" + position;
    }

    public Component asComponent(ResourceKey<Level> dimension) {
        // 无坐标：仅文本，不可点击
        if (pos == null) {
            return Component.literal(getName() + "@null");
        }

        DisplayName displayName = resolveDisplayName(getName());

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        MutableComponent message = Component.empty();
        // 普通部分：名字 + @
        Component base = Component.literal(displayName.text() + "@");
        if (displayName.hoverName() != null) {
            // 方块带 modId 时附悬浮提示
            base = base.copy().setStyle(
                    Style.EMPTY.withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal(displayName.hoverName())
                    ))
            );
        }

        // 坐标部分：金色 + 可点击
        Component coord = Component.literal("(" + x + "," + y + "," + z + ")")
                .setStyle(
                        Style.EMPTY
                                .withColor(TextColor.fromRgb(0xFFD700))
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/" + ReportButtons.teleportCommand(dimension.location(), pos)
                                ))
                );

        return message.append(base).append(coord);
    }

    static DisplayName resolveDisplayName(String rawName) {
        // 形如 "Block{modid:blockid}" 时，拆出简短名并把 modId 作为悬浮提示
        if (rawName.startsWith("Block{") && rawName.endsWith("}")) {
            int start = "Block{".length();
            int colon = rawName.indexOf(':', start);
            if (colon > start) {
                int end = rawName.length() - 1;
                String modId = rawName.substring(start, colon);
                String blockId = rawName.substring(colon + 1, end);
                return new DisplayName("Block{" + blockId + "}", modId);
            }
        }
        return new DisplayName(rawName, null);
    }

    record DisplayName(String text, String hoverName) {
    }
}
