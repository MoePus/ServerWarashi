package com.moepus.serverwarashi.common.mixin.watchdog;

import java.util.List;

final class WatchdogTicketFormatter {
    private static final String NO_ACTIVE_OWNERS = "No active ticket owners.";

    private WatchdogTicketFormatter() {
    }

    static String format(List<Dimension> dimensions) {
        if (dimensions.isEmpty()) {
            return "No server dimensions available.";
        }
        StringBuilder builder = new StringBuilder();
        for (Dimension dimension : dimensions) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            appendDimension(builder, dimension);
        }
        return builder.toString();
    }

    private static void appendDimension(StringBuilder builder, Dimension dimension) {
        builder.append("Dimension: ").append(dimension.name()).append('\n');
        if (dimension.error() != null) {
            builder.append("Failed to collect active ticket owners: ").append(dimension.error());
            return;
        }
        builder.append("Running ticket owners:");
        if (dimension.owners().isEmpty()) {
            builder.append('\n').append(NO_ACTIVE_OWNERS);
            return;
        }
        dimension.owners().forEach(owner -> appendOwner(builder, owner));
    }

    private static void appendOwner(StringBuilder builder, Owner owner) {
        builder.append("\n- ").append(owner.name()).append('@');
        appendPosition(builder, owner.position());
        Counts counts = owner.counts();
        builder.append(" (chunks=").append(counts.chunks())
                .append(", BE=").append(counts.blockEntities())
                .append(", E=").append(counts.entities())
                .append(')');
    }

    private static void appendPosition(StringBuilder builder, Position position) {
        if (position == null) {
            builder.append("null");
            return;
        }
        builder.append('(').append(position.x())
                .append(", ").append(position.y())
                .append(", ").append(position.z())
                .append(')');
    }

    record Dimension(String name, List<Owner> owners, String error) {
        Dimension {
            owners = List.copyOf(owners);
        }

        static Dimension success(String name, List<Owner> owners) {
            return new Dimension(name, owners, null);
        }

        static Dimension failure(String name, Exception exception) {
            return new Dimension(name, List.of(), exception.getClass().getSimpleName());
        }
    }

    record Owner(String name, Position position, Counts counts) {
    }

    record Position(int x, int y, int z) {
    }

    record Counts(int chunks, int blockEntities, int entities) {
    }
}
