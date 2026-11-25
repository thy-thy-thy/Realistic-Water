package me.skaffy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.skaffy.client.cloth.ClothGrid;
import me.skaffy.client.cloth.ClothManager;
import me.skaffy.client.cloth.ClothMeshRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerRenderer.class)
public abstract class BannerRendererMixin {
	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BannerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void realisticWater$submitClothBanner(
		BannerRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		net.minecraft.client.renderer.state.CameraRenderState cameraState,
		CallbackInfo ci
	) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		BlockPos pos = state.blockPos;
		float yawRad = -state.angle * Mth.DEG_TO_RAD;
		double rightX = Math.cos(yawRad);
		double rightZ = Math.sin(yawRad);
		double outX = -Math.sin(yawRad);
		double outZ = Math.cos(yawRad);

		double barY = pos.getY() + (state.standing ? 1.82 : 1.48);
		double centerX = pos.getX() + 0.5 + outX * (state.standing ? 0.08 : -0.36);
		double centerZ = pos.getZ() + 0.5 + outZ * (state.standing ? 0.08 : -0.36);

		double halfW = 0.4375;
		Vec3 topLeft = new Vec3(centerX - rightX * halfW, barY, centerZ - rightZ * halfW);
		Vec3 topRight = new Vec3(centerX + rightX * halfW, barY, centerZ + rightZ * halfW);
		Vec3 outward = new Vec3(outX, 0.0, outZ);

		ClothGrid grid = ClothManager.banner(level, pos, topLeft, topRight, outward);
		int light = state.lightCoords;
		DyeColor baseColor = state.baseColor;
		BannerPatternLayers patterns = state.patterns;

		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(net.minecraft.client.renderer.Sheets.BANNER_SHEET), (pose, consumer) -> {
			PoseStack localStack = new PoseStack();
			localStack.last().pose().set(pose.pose());
			localStack.last().normal().set(pose.normal());

			MaterialSet materials = Minecraft.getInstance().getAtlasManager();
			Material baseMat = net.minecraft.client.renderer.Sheets.BANNER_BASE;
			ClothMeshRenderer.renderSprite(
				localStack, consumer, grid,
				pos.getX(), pos.getY(), pos.getZ(),
				materials.get(baseMat), light, ARGB.opaque(baseColor.getTextureDiffuseColor())
			);

			for (BannerPatternLayers.Layer layer : patterns.layers()) {
				Material patternMat = net.minecraft.client.renderer.Sheets.getBannerMaterial(layer.pattern());
				ClothMeshRenderer.renderSprite(
					localStack, consumer, grid,
					pos.getX(), pos.getY(), pos.getZ(),
					materials.get(patternMat), light, ARGB.opaque(layer.color().getTextureDiffuseColor())
				);
			}
		});

		ci.cancel();
	}
}
