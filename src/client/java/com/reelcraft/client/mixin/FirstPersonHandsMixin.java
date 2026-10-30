package com.reelcraft.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reelcraft.client.ReelsClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
	private static final String SUBMIT_ARM = "submitArmWithItem";

	/** The hands are drawn at the edge of the full window, which the portrait pillarbox hides; pull them into the column. */
	@Inject(method = SUBMIT_ARM, at = @At("HEAD"))
	private void reelcraft$shiftIn(
		PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float a, float b, InteractionHand hand, float c, ItemStack item, float d,
		PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci
	) {
		poseStack.translate(ReelsClient.handShift(hand), 0.0F, 0.0F);
	}

	@Inject(method = SUBMIT_ARM, at = @At("RETURN"))
	private void reelcraft$shiftBack(
		PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float a, float b, InteractionHand hand, float c, ItemStack item, float d,
		PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci
	) {
		poseStack.translate(-ReelsClient.handShift(hand), 0.0F, 0.0F);
	}
}
