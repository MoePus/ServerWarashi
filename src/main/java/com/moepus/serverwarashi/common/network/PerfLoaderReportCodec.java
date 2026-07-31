package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

final class PerfLoaderReportCodec {
    private PerfLoaderReportCodec() {
    }

    static void write(RegistryFriendlyByteBuf buffer,
                      List<PerfReportData.LoaderSourceEntry> sources,
                      PerfReportCodec.EntryBudget budget) {
        PerfReportCodec.requireSize(sources.size(), PerfReportLimits.MAX_LOADER_SOURCES, "loader source");
        budget.claimEntries(sources.size());
        buffer.writeVarInt(sources.size());
        for (PerfReportData.LoaderSourceEntry source : sources) {
            PerfReportCodec.writeString(buffer, source.dimension(), budget);
            PerfReportCodec.writeString(buffer, source.sourceId(), budget);
            PerfReportCodec.writeString(buffer, source.loaderName(), budget);
            writePosition(buffer, source.position());
            writeControl(buffer, source.control(), budget);
            buffer.writeVarInt(source.omittedRegions());
            writeRegions(buffer, source.regions(), budget);
        }
    }

    static List<PerfReportData.LoaderSourceEntry> read(
            RegistryFriendlyByteBuf buffer, PerfReportCodec.EntryBudget budget) {
        int size = PerfReportCodec.readSize(
                buffer, PerfReportLimits.MAX_LOADER_SOURCES, "loader source", budget);
        List<PerfReportData.LoaderSourceEntry> sources = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            String dimension = PerfReportCodec.readString(buffer, budget);
            String sourceId = PerfReportCodec.readString(buffer, budget);
            String loaderName = PerfReportCodec.readString(buffer, budget);
            PerfReportData.LoaderPosition position = readPosition(buffer);
            PerfReportData.LoaderControl control = readControl(buffer, budget);
            int omittedRegions = buffer.readVarInt();
            sources.add(new PerfReportData.LoaderSourceEntry(dimension, sourceId, loaderName, position,
                    omittedRegions, readRegions(buffer, budget), control));
        }
        return List.copyOf(sources);
    }

    private static void writeRegions(RegistryFriendlyByteBuf buffer,
                                     List<PerfReportData.LoaderRegion> regions,
                                     PerfReportCodec.EntryBudget budget) {
        PerfReportCodec.requireSize(regions.size(), PerfReportLimits.MAX_LOADER_REGIONS, "loader region");
        budget.claimLoaderRegions(regions.size());
        budget.claimEntries(regions.size());
        buffer.writeVarInt(regions.size());
        regions.forEach(region -> {
            buffer.writeInt(region.minChunkX());
            buffer.writeInt(region.minChunkZ());
            buffer.writeVarInt(region.width());
            buffer.writeVarInt(region.height());
        });
    }

    private static List<PerfReportData.LoaderRegion> readRegions(
            RegistryFriendlyByteBuf buffer, PerfReportCodec.EntryBudget budget) {
        int size = PerfReportCodec.readSize(
                buffer, PerfReportLimits.MAX_LOADER_REGIONS, "loader region", budget);
        budget.claimLoaderRegions(size);
        List<PerfReportData.LoaderRegion> regions = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            regions.add(new PerfReportData.LoaderRegion(
                    buffer.readInt(), buffer.readInt(), buffer.readVarInt(), buffer.readVarInt()));
        }
        return List.copyOf(regions);
    }

    private static void writePosition(
            RegistryFriendlyByteBuf buffer, PerfReportData.LoaderPosition position) {
        buffer.writeBoolean(position != null);
        if (position != null) {
            buffer.writeInt(position.x());
            buffer.writeInt(position.y());
            buffer.writeInt(position.z());
        }
    }

    private static PerfReportData.LoaderPosition readPosition(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? new PerfReportData.LoaderPosition(buffer.readInt(), buffer.readInt(), buffer.readInt())
                : null;
    }

    private static void writeControl(RegistryFriendlyByteBuf buffer,
                                     PerfReportData.LoaderControl control,
                                     PerfReportCodec.EntryBudget budget) {
        buffer.writeBoolean(control != null);
        if (control == null) {
            return;
        }
        PerfReportCodec.writeString(buffer, control.controllerId().toString(), budget);
        BlockPos pos = control.ownerPos();
        buffer.writeInt(pos.getX());
        buffer.writeInt(pos.getY());
        buffer.writeInt(pos.getZ());
        buffer.writeBoolean(control.manuallyFrozen());
        buffer.writeBoolean(control.working());
    }

    private static PerfReportData.LoaderControl readControl(
            RegistryFriendlyByteBuf buffer, PerfReportCodec.EntryBudget budget) {
        if (!buffer.readBoolean()) {
            return null;
        }
        String rawController = PerfReportCodec.readString(buffer, budget);
        ResourceLocation controllerId = ResourceLocation.tryParse(rawController);
        if (controllerId == null) {
            throw new IllegalArgumentException("Invalid loader controller ID: " + rawController);
        }
        BlockPos ownerPos = new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt());
        return new PerfReportData.LoaderControl(
                controllerId, ownerPos, buffer.readBoolean(), buffer.readBoolean());
    }
}
