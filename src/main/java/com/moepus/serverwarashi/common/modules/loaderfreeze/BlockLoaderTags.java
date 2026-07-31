package com.moepus.serverwarashi.common.modules.loaderfreeze;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class BlockLoaderTags {
    public static final TagKey<Block> ATTRIBUTION_CANDIDATES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "chunk_loaders"));

    private BlockLoaderTags() {
    }
}
