package com.moepus.serverwarashi.common.modules.weakload;

import com.moepus.serverwarashi.common.mixin.accessor.EntityLevelCallbackAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.PersistentEntityCallbackAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class WeakChunkBatchRemoval {
    private WeakChunkBatchRemoval() {
    }

    static Target target(Entity entity, EntitySection<Entity> section) {
        EntityInLevelCallback levelCallback = levelCallback(entity);
        if (!(levelCallback instanceof PersistentEntityCallbackAccessor callback)
                || callback.serverwarashi$getCurrentSection() != section) {
            return null;
        }
        return new Target(entity, levelCallback, callback);
    }

    static Result clear(SectionRef sectionRef, List<Target> targets) {
        EntitySection<Entity> section = sectionRef.section();
        BatchRemovalSection recording = new BatchRemovalSection(section.getStatus());
        List<Target> prepared = new ArrayList<>(targets.size());
        int removed = 0;
        try {
            // Redirect the whole batch first so reentrant callbacks cannot clean the real mapping.
            for (Target target : targets) {
                if (!isStillEligible(target, section)) {
                    continue;
                }
                prepared.add(target);
                target.callback().serverwarashi$setCurrentSection(recording);
            }
            detach(section, prepared);
            for (Target target : prepared) {
                if (discard(target, recording)) {
                    removed++;
                }
            }
        } finally {
            restoreSurvivors(sectionRef, prepared, recording);
            removeSectionIfEmpty(sectionRef);
        }
        return new Result(targets.size(), removed);
    }

    private static void detach(EntitySection<Entity> section, List<Target> targets) {
        Set<Entity> identities = identitySet();
        targets.forEach(target -> identities.add(target.entity()));
        WeakChunkEntityAccess.classLists(section)
                .forEach(list -> list.removeIf(identities::contains));
    }

    private static boolean discard(Target target, BatchRemovalSection recording) {
        if (target.entity().isRemoved()
                || levelCallback(target.entity()) != target.original()
                || target.callback().serverwarashi$getCurrentSection() != recording) {
            return false;
        }
        target.entity().discard();
        return target.entity().isRemoved();
    }

    private static boolean isStillEligible(Target target, EntitySection<Entity> section) {
        return !target.entity().isRemoved()
                && levelCallback(target.entity()) == target.original()
                && target.callback().serverwarashi$getCurrentSection() == section;
    }

    private static void restoreSurvivors(SectionRef sectionRef,
                                         List<Target> targets,
                                         BatchRemovalSection recording) {
        RestoreContext restore = null;
        for (Target target : targets) {
            EntityInLevelCallback liveCallback = levelCallback(target.entity());
            boolean callbackEntered = recording.contains(target.entity());
            RestoreAction action = restoreAction(callbackEntered,
                    !callbackEntered && hasManagedLocation(target.entity(), liveCallback));
            if (action == RestoreAction.CALLBACK_ENTERED) {
                restoreRecordedCallback(sectionRef.section(), target, recording);
                continue;
            }
            if (action == RestoreAction.MANAGED) {
                continue;
            }
            if (restore == null) {
                restore = new RestoreContext(resolveSection(sectionRef), targets);
            }
            target.callback().serverwarashi$setCurrentSection(restore.section());
            restore.addIfMissing(target.entity());
        }
    }

    static RestoreAction restoreAction(boolean callbackEntered, boolean managedLocation) {
        if (callbackEntered) {
            return RestoreAction.CALLBACK_ENTERED;
        }
        return managedLocation ? RestoreAction.MANAGED : RestoreAction.RESTORE;
    }

    private static void restoreRecordedCallback(EntitySection<Entity> original,
                                                Target target,
                                                BatchRemovalSection recording) {
        EntityInLevelCallback liveCallback = levelCallback(target.entity());
        if (liveCallback == target.original()
                && target.callback().serverwarashi$getCurrentSection() == recording) {
            target.callback().serverwarashi$setCurrentSection(original);
        }
    }

    private static boolean hasManagedLocation(Entity entity, EntityInLevelCallback levelCallback) {
        if (!(levelCallback instanceof PersistentEntityCallbackAccessor callback)) {
            return false;
        }
        return callback.serverwarashi$getCurrentSection().getEntities()
                .anyMatch(candidate -> candidate == entity);
    }

    private static EntitySection<Entity> resolveSection(SectionRef sectionRef) {
        EntitySection<Entity> mapped = sectionRef.storage().getSection(sectionRef.sectionId());
        return mapped != null ? mapped : sectionRef.storage().getOrCreateSection(sectionRef.sectionId());
    }

    private static void removeSectionIfEmpty(SectionRef sectionRef) {
        EntitySection<Entity> section = sectionRef.section();
        if (section.isEmpty()
                && sectionRef.storage().getSection(sectionRef.sectionId()) == section) {
            sectionRef.storage().remove(sectionRef.sectionId());
        }
    }

    private static EntityInLevelCallback levelCallback(Entity entity) {
        return ((EntityLevelCallbackAccessor) entity).serverwarashi$getLevelCallback();
    }

    private static Set<Entity> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    record Target(Entity entity,
                  EntityInLevelCallback original,
                  PersistentEntityCallbackAccessor callback) {
    }

    record SectionRef(EntitySectionStorage<Entity> storage,
                      long sectionId,
                      EntitySection<Entity> section) {
    }

    record Result(int attempted, int removed) {
        static final Result EMPTY = new Result(0, 0);
    }

    enum RestoreAction {
        CALLBACK_ENTERED,
        MANAGED,
        RESTORE
    }

    private static final class RestoreContext {
        private final EntitySection<Entity> section;
        private final Set<Entity> present = identitySet();

        private RestoreContext(EntitySection<Entity> section, List<Target> targets) {
            this.section = section;
            Set<Entity> candidates = identitySet();
            targets.forEach(target -> candidates.add(target.entity()));
            for (Entity entity : WeakChunkEntityAccess.entities(section)) {
                if (candidates.contains(entity)) {
                    present.add(entity);
                }
            }
        }

        private EntitySection<Entity> section() {
            return section;
        }

        private void addIfMissing(Entity entity) {
            if (present.add(entity)) {
                section.add(entity);
            }
        }
    }

    private static final class BatchRemovalSection extends EntitySection<Entity> {
        private final Set<Entity> removals = identitySet();

        private BatchRemovalSection(Visibility visibility) {
            super(Entity.class, visibility);
        }

        @Override
        public boolean remove(Entity entity) {
            removals.add(entity);
            return true;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        private boolean contains(Entity entity) {
            return removals.contains(entity);
        }
    }
}
