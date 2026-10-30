package com.reelcraft.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Structure templates are shared by every level, and a palette's lazy caches are plain unsynchronized maps. Vanilla
 * rarely generates villages in two levels at once; the feed generates several worlds in parallel, so guard them.
 */
@Mixin(StructureTemplate.Palette.class)
public abstract class StructurePaletteMixin {
	@WrapMethod(method = "blocks(Lnet/minecraft/world/level/block/Block;)Ljava/util/List;")
	private List<StructureTemplate.StructureBlockInfo> reelcraft$lockedBlocks(Block block, Operation<List<StructureTemplate.StructureBlockInfo>> original) {
		synchronized (this) {
			return original.call(block);
		}
	}

	@WrapMethod(method = "jigsaws")
	private List<StructureTemplate.JigsawBlockInfo> reelcraft$lockedJigsaws(Operation<List<StructureTemplate.JigsawBlockInfo>> original) {
		synchronized (this) {
			return original.call();
		}
	}
}
