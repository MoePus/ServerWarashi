package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.LoaderControlStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPerfReportStateTest {
    private static final long REPORT_ID = 7L;
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation CONTROLLER = ResourceLocation.parse("test:loader");
    private static final BlockPos OWNER = new BlockPos(8, 64, -8);

    @Test
    void patchesOnlyMatchingAuthoritativeState() {
        ClientPerfReportState.update(report(false, true), false);
        long before = ClientPerfReportState.snapshot().revision();

        boolean changed = ClientPerfReportState.applyLoaderControl(payload(new PayloadSpec(
                REPORT_ID, CONTROLLER, OWNER, true, false,
                LoaderControlStatePayload.Status.APPLIED)));

        assertTrue(changed);
        assertEquals(before + 1L, ClientPerfReportState.snapshot().revision());
        assertEquals(new PerfReportData.LoaderControl(CONTROLLER, OWNER, true, false),
                control());
    }

    @Test
    void rejectsStaleFailedAndWrongIdentityUpdates() {
        ClientPerfReportState.update(report(false, true), false);
        long revision = ClientPerfReportState.snapshot().revision();

        assertFalse(ClientPerfReportState.applyLoaderControl(payload(new PayloadSpec(
                REPORT_ID - 1L, CONTROLLER, OWNER, true, false,
                LoaderControlStatePayload.Status.SYNC))));
        assertFalse(ClientPerfReportState.applyLoaderControl(payload(new PayloadSpec(
                REPORT_ID, CONTROLLER, OWNER, false, false,
                LoaderControlStatePayload.Status.STALE))));
        assertFalse(ClientPerfReportState.applyLoaderControl(payload(new PayloadSpec(
                REPORT_ID, ResourceLocation.parse("test:other"), OWNER, true, false,
                LoaderControlStatePayload.Status.SYNC))));

        assertEquals(revision, ClientPerfReportState.snapshot().revision());
        assertEquals(new PerfReportData.LoaderControl(CONTROLLER, OWNER, false, true), control());
    }

    @Test
    void identicalAuthoritativeStateDoesNotRebuildClientModel() {
        ClientPerfReportState.update(report(false, true), true);
        long revision = ClientPerfReportState.snapshot().revision();

        assertFalse(ClientPerfReportState.applyLoaderControl(payload(new PayloadSpec(
                REPORT_ID, CONTROLLER, OWNER, false, true,
                LoaderControlStatePayload.Status.UNCHANGED))));

        assertEquals(revision, ClientPerfReportState.snapshot().revision());
        assertTrue(ClientPerfReportState.snapshot().profiling());
    }

    private static LoaderControlStatePayload payload(PayloadSpec spec) {
        return new LoaderControlStatePayload(
                spec.reportId(), DIMENSION, spec.controller(), spec.owner(),
                spec.frozen(), spec.working(), spec.status());
    }

    private static PerfReportData report(boolean frozen, boolean working) {
        var control = new PerfReportData.LoaderControl(CONTROLLER, OWNER, frozen, working);
        var loader = new PerfReportData.LoaderSourceEntry(
                DIMENSION.toString(), "source", "Loader",
                new PerfReportData.LoaderPosition(OWNER.getX(), OWNER.getY(), OWNER.getZ()),
                0, List.of(new PerfReportData.LoaderRegion(0, -1, 1, 1)), control);
        return new PerfReportData(REPORT_ID, 0.0D, 0L, 0L, 0L, 0L, 1_000,
                0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(loader));
    }

    private static PerfReportData.LoaderControl control() {
        return ClientPerfReportState.snapshot().report().loaderSources().getFirst().control();
    }

    private record PayloadSpec(long reportId, ResourceLocation controller, BlockPos owner,
                               boolean frozen, boolean working,
                               LoaderControlStatePayload.Status status) {
    }
}
