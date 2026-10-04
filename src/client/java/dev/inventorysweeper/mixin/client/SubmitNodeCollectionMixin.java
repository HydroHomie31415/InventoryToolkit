package dev.inventorysweeper.mixin.client;

import dev.inventorysweeper.render.DroppedItemRenderContext;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
	@ModifyArgs(
		method = "submitItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer$Submit;<init>(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V",
			ordinal = 0
		)
	)
	private void inventorySweeper$applyOpacity(Args args) {
		float opacity = DroppedItemRenderContext.opacity();
		if (opacity >= 0.999F) {
			return;
		}

		int[] originalTints = args.get(5);
		List<BakedQuad> originalQuads = args.get(6);
		int maxTintIndex = 0;
		List<BakedQuad> translucentQuads = new ArrayList<>(originalQuads.size());

		for (BakedQuad quad : originalQuads) {
			BakedQuad.MaterialInfo material = quad.materialInfo();
			int tintIndex = Math.max(0, material.tintIndex());
			maxTintIndex = Math.max(maxTintIndex, tintIndex);
			RenderType renderType = material.sprite().atlasLocation().equals(Sheets.BLOCKS_MAPPER.sheet())
				? Sheets.translucentBlockItemSheet()
				: Sheets.translucentItemSheet();
			BakedQuad.MaterialInfo translucentMaterial = new BakedQuad.MaterialInfo(
				material.sprite(),
				material.layer(),
				renderType,
				material.itemGlintRenderType(),
				material.itemGlintSpecialRenderType(),
				tintIndex,
				material.shadeDirectionOverride(),
				material.lightEmission()
			);
			translucentQuads.add(new BakedQuad(
				quad.position0(),
				quad.position1(),
				quad.position2(),
				quad.position3(),
				quad.packedUV0(),
				quad.packedUV1(),
				quad.packedUV2(),
				quad.packedUV3(),
				quad.direction(),
				translucentMaterial
			));
		}

		int[] translucentTints = Arrays.copyOf(originalTints, Math.max(originalTints.length, maxTintIndex + 1));
		for (int index = 0; index < translucentTints.length; index++) {
			int color = index < originalTints.length ? originalTints[index] : 0xFFFFFFFF;
			int originalAlpha = color >>> 24;
			int adjustedAlpha = Math.round(originalAlpha * opacity);
			translucentTints[index] = (adjustedAlpha << 24) | (color & 0x00FFFFFF);
		}

		args.set(5, translucentTints);
		args.set(6, translucentQuads);
		args.set(7, ItemStackRenderState.FoilType.NONE);
	}
}
