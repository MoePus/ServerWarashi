package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@GameTestHolder(ServerWarashi.MODID)
@PrefixGameTestTemplate(false)
public final class TicketTrackerGameTests {
    private static final String TEMPLATE = "bastion/mobs/empty";
    private static final int STRONG_DISTANCE = 2;
    private static final int WEAK_DISTANCE = 1;
    private static final int MAIN_ONLY_LEVEL = 31;
    private static final TicketType<String> TEST_TICKET =
            TicketType.create("serverwarashi_tracker_test", Comparator.<String>naturalOrder());

    private TicketTrackerGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void trackersMatchMaskedTicketLevel(GameTestHelper helper) {
        run(helper, fixture -> {
            Ticket<?> ticket = fixture.addRegion("single", STRONG_DISTANCE);
            fixture.pause(ticket, true);
            fixture.assertLevels(TicketUtils.PAUSED_TICKET_LEVEL,
                    TicketUtils.PAUSED_TICKET_LEVEL);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void mainOnlyTicketDoesNotEnterTickingTrackerOnRestore(GameTestHelper helper) {
        run(helper, fixture -> {
            Ticket<?> region = fixture.addRegion("region", WEAK_DISTANCE);
            int regionLevel = TicketUtils.getOriginLevel(region);
            Ticket<?> mainOnly = fixture.addMainOnly("main", MAIN_ONLY_LEVEL);
            fixture.pause(mainOnly, true);
            fixture.assertLevels(regionLevel, regionLevel);

            fixture.pause(mainOnly, false);
            fixture.assertLevels(MAIN_ONLY_LEVEL, regionLevel);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void trackersRecoverAfterManualPauseCleared(GameTestHelper helper) {
        run(helper, fixture -> {
            Ticket<?> ticket = fixture.addRegion("recover", STRONG_DISTANCE);
            int rawLevel = TicketUtils.getOriginLevel(ticket);
            fixture.pause(ticket, true);
            fixture.pause(ticket, false);
            fixture.assertLevels(rawLevel, rawLevel);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void trackersUseMaskedMinimumAcrossMixedTickets(GameTestHelper helper) {
        run(helper, fixture -> {
            Ticket<?> strict = fixture.addRegion("strict", STRONG_DISTANCE);
            Ticket<?> weak = fixture.addRegion("weak", WEAK_DISTANCE);
            fixture.pause(strict, true);

            int expected = TicketUtils.getOriginLevel(weak);
            fixture.assertMaskedMinimum(expected);
            fixture.assertLevels(expected, expected);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void trackersHoldMaskedMinimumWhenStrictestTicketPaused(GameTestHelper helper) {
        run(helper, fixture -> {
            Ticket<?> strict = fixture.addRegion("strict-recompute", STRONG_DISTANCE);
            fixture.pause(strict, true);
            Ticket<?> weak = fixture.addRegion("weak-recompute", WEAK_DISTANCE);

            int expected = TicketUtils.getOriginLevel(weak);
            fixture.assertLevels(expected, expected);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void trackerSourceStaysMaskedMinAfterUnrelatedTicketRemoval(GameTestHelper helper) {
        run(helper, fixture -> {
            Ticket<?> strict = fixture.addRegion("strict-remove", STRONG_DISTANCE);
            Ticket<?> weak = fixture.addRegion("weak-remove", WEAK_DISTANCE);
            fixture.pause(strict, true);
            Registration unrelated = fixture.addRegionRegistration("unrelated", 0);
            fixture.remove(unrelated);

            int expected = TicketUtils.getOriginLevel(weak);
            fixture.assertLevels(expected, expected);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE)
    public static void adjacentTrackerConsistencySurvivesNeighborPropagation(GameTestHelper helper) {
        run(helper, fixture -> {
            Ticket<?> strict = fixture.addRegion("strict-neighbor", STRONG_DISTANCE);
            Ticket<?> weak = fixture.addRegion("weak-neighbor", WEAK_DISTANCE);
            fixture.pause(strict, true);
            Registration neighbor = fixture.addNeighbor("neighbor", STRONG_DISTANCE);
            fixture.remove(neighbor);

            int expected = TicketUtils.getOriginLevel(weak);
            fixture.assertLevels(expected, expected);
        });
    }

    private static void run(GameTestHelper helper, Scenario scenario) {
        try (TrackerFixture fixture = new TrackerFixture(helper)) {
            scenario.run(fixture);
            helper.succeed();
        }
    }

    @FunctionalInterface
    private interface Scenario {
        void run(TrackerFixture fixture);
    }

    private static final class TrackerFixture implements AutoCloseable {
        private static final int CHUNK_OFFSET = 10;

        private final GameTestHelper helper;
        private final DistanceManager manager;
        private final DistanceManagerAccessor access;
        private final ChunkPos chunk;
        private final List<Registration> registrations = new ArrayList<>();

        private TrackerFixture(GameTestHelper helper) {
            this.helper = helper;
            var level = helper.getLevel();
            manager = level.getChunkSource().chunkMap.getDistanceManager();
            access = (DistanceManagerAccessor) manager;
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            ChunkPos originChunk = new ChunkPos(origin);
            chunk = new ChunkPos(originChunk.x + CHUNK_OFFSET, originChunk.z + CHUNK_OFFSET);
        }

        Ticket<?> addRegion(String key, int distance) {
            return ticket(addRegionRegistration(key, distance));
        }

        Registration addRegionRegistration(String key, int distance) {
            return add(new Registration(chunk, key, distance, true));
        }

        Ticket<?> addMainOnly(String key, int level) {
            return ticket(add(new Registration(chunk, key, level, false)));
        }

        Registration addNeighbor(String key, int distance) {
            return add(new Registration(new ChunkPos(chunk.x + 1, chunk.z), key, distance, true));
        }

        private Registration add(Registration registration) {
            if (registration.region()) {
                manager.addRegionTicket(TEST_TICKET, registration.chunk(),
                        registration.levelOrDistance(), registration.key());
            } else {
                manager.addTicket(TEST_TICKET, registration.chunk(),
                        registration.levelOrDistance(), registration.key());
            }
            registrations.add(registration);
            refresh();
            return registration;
        }

        void remove(Registration registration) {
            Ticket<?> ticket = findTicket(registration);
            if (ticket != null && TicketUtils.asPauseable(ticket).serverWarashi$isPaused()) {
                TicketPauseBatch.updateTicketPauseReason(
                        ticket, false, PauseableTicket.PAUSE_REASON_MANUAL);
            }
            removeTicket(registration);
            registrations.remove(registration);
            refresh();
        }

        void pause(Ticket<?> ticket, boolean paused) {
            TicketPauseBatch batch = new TicketPauseBatch(access);
            if (!batch.apply(ticket, chunk.toLong(), paused,
                    PauseableTicket.PAUSE_REASON_MANUAL)) {
                helper.fail("Ticket pause mask did not change");
            }
            batch.flush();
            refresh();
        }

        void assertMaskedMinimum(int expected) {
            Ticket<?> actual = TicketUtils.strictestMaskedTicket(access.getTickets().get(chunk.toLong()));
            assertEquals("masked ticket minimum", expected, actual.getTicketLevel());
        }

        void assertLevels(int expectedMain, int expectedTicking) {
            assertEquals("main tracker", expectedMain,
                    access.getTicketTracker().getLevel(chunk.toLong()));
            assertEquals("ticking tracker", expectedTicking,
                    access.getTickingTicketsTracker().getLevel(chunk));
        }

        private void assertEquals(String tracker, int expected, int actual) {
            if (actual != expected) {
                helper.fail(tracker + " level was " + actual + ", expected " + expected);
            }
        }

        private Ticket<?> ticket(Registration registration) {
            Ticket<?> ticket = findTicket(registration);
            if (ticket == null) {
                helper.fail("Ticket was not inserted: " + registration.key());
            }
            return ticket;
        }

        private Ticket<?> findTicket(Registration registration) {
            var tickets = access.getTickets().get(registration.chunk().toLong());
            if (tickets == null) {
                return null;
            }
            for (Ticket<?> ticket : tickets) {
                if (registration.key().equals(TicketUtils.asPauseable(ticket).serverWarashi$getKey())) {
                    return ticket;
                }
            }
            return null;
        }

        private void refresh() {
            manager.runAllUpdates(helper.getLevel().getChunkSource().chunkMap);
        }

        private void removeTicket(Registration registration) {
            if (registration.region()) {
                manager.removeRegionTicket(TEST_TICKET, registration.chunk(),
                        registration.levelOrDistance(), registration.key());
            } else {
                manager.removeTicket(TEST_TICKET, registration.chunk(),
                        registration.levelOrDistance(), registration.key());
            }
        }

        @Override
        public void close() {
            for (int index = registrations.size() - 1; index >= 0; index--) {
                Registration registration = registrations.get(index);
                Ticket<?> ticket = findTicket(registration);
                if (ticket != null) {
                    TicketPauseBatch.updateTicketPauseReason(
                            ticket, false, PauseableTicket.PAUSE_REASON_MANUAL);
                }
                removeTicket(registration);
            }
            registrations.clear();
            refresh();
        }
    }

    private record Registration(ChunkPos chunk, String key,
                                int levelOrDistance, boolean region) {
    }
}
