package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C2meNoTickAttributionTest {
    @Test
    void recognizesOnlyTheExactTicketTypeName() {
        assertTrue(C2meNoTickAttribution.isTicketType("c2me_notickvd"));
        assertFalse(C2meNoTickAttribution.isTicketType("c2me:notickvd"));
        assertFalse(C2meNoTickAttribution.isTicketType("c2me_notickvd_extra"));
    }

    @Test
    void addsC2meViewDistancePadding() {
        assertEquals(13, C2meNoTickAttribution.radius(12));
    }

    @Test
    void captureContextReusesPlayerBucketAndFallsBackToUnknown() {
        var chunks = new HashMap<ChunkKey, java.util.Set<PerfAttributionSnapshot.Bucket>>();
        var target = new C2meNoTickAttribution.CaptureTarget("test:level", chunks);
        var bucket = playerBucket();
        var player = new C2meNoTickAttribution.PlayerRange(10, -10, bucket);
        var context = new C2meNoTickAttribution.CaptureContext(2, List.of(player), target);

        long covered = ChunkPos.asLong(12, -8);
        long alsoCovered = ChunkPos.asLong(8, -12);
        long outside = ChunkPos.asLong(13, -8);
        context.capture(covered);
        context.capture(alsoCovered);
        context.capture(outside);

        assertSame(bucket, chunks.get(new ChunkKey("test:level", covered)).iterator().next());
        assertSame(bucket, chunks.get(new ChunkKey("test:level", alsoCovered)).iterator().next());
        assertEquals(java.util.Set.of(PerfAttributionSnapshot.UNKNOWN),
                chunks.get(new ChunkKey("test:level", outside)));
    }

    private static PerfAttributionSnapshot.Bucket playerBucket() {
        var owner = new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.PLAYER,
                "player", "Player", true, 0L, null);
        var source = new PerfAttributionSnapshot.Source("c2me:player", "c2me_notickvd", null);
        return new PerfAttributionSnapshot.Bucket(owner, source, 0, 0);
    }
}
