package me.skaffy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.skaffy.client.cloth.ClothGrid;
import me.skaffy.client.cloth.ClothManager;
import me.skaffy.client.cloth.ClothMeshRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerRenderer.class)
public abstract class BannerRendererMixin {
	@Shadow
	@Final
	private SpriteGetter materials;

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BannerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void realisticWater$submitClothBanner(
		BannerRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState cameraState,
		CallbackInfo ci
	) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || state.transformation == null) {
			return;
		}

		BlockPos pos = state.blockPos;
		boolean ground = state.attachmentType == BannerBlock.AttachmentType.GROUND;

		// Transform banner top-bar endpoints directly from BannerRenderState's exact Transformation matrix
		Matrix4fc mat = state.transformation.getMatrix();
		float barY = ground ? 1.82F : 1.48F;
		float barZ = ground ? 0.08F : -0.36F;
		float halfW = 0.4375F;

		Vector3f pL = mat.transformPosition(-halfW, barY, barZ, new Vector3f());
		Vector3f pR = mat.transformPosition(halfW, barY, barZ, new Vector3f());
		Vector3f out = mat.transformDirection(0.0F, 0.0F, 1.0F, new Vector3f()).normalize();

		Vec3 topLeft = new Vec3(pos.getX() + pL.x, pos.getY() + pL.y, pos.getZ() + pL.z);
		Vec3 topRight = new Vec3(pos.getX() + pR.x, pos.getY() + pR.y, pos.getZ() + pR.z);
		Vec3 outward = new Vec3(out.x, out.y, out.z);

		ClothGrid grid = ClothManager.banner(level, pos, topLeft, topRight, outward);
		int light = state.lightCoords;
		DyeColor baseColor = state.baseColor;
		BannerPatternLayers patterns = state.patterns;
		SpriteGetter sprites = this.materials;

		collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(Sheets.BANNER_SHEET), (pose, consumer) -> {
			PoseStack localStack = new PoseStack();
			localStack.last().pose().set(pose.pose());
			localStack.last().normal().set(pose.normal());

			ClothMeshRenderer.renderSprite(
				localStack, consumer, grid,
				pos.getX(), pos.getY(), pos.getZ(),
				sprites.get(Sheets.BANNER_BASE), light, ARGB.opaque(baseColor.getTextureDiffuseColor())
			);

			for (BannerPatternLayers.Layer layer : patterns.layers()) {
				SpriteId patternId = Sheets.getBannerSprite(layer.pattern());
				ClothMeshRenderer.renderSprite(
					localStack, consumer, grid,
					pos.getX(), pos.getY(), pos.getZ(),
					sprites.get(patternId), light, ARGB.opaque(layer.color().getTextureDiffuseColor())
				);
			}
		});

		ci.cancel();
	}
}
