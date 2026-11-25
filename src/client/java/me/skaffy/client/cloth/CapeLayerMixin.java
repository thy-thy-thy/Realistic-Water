package me.skaffy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.skaffy.client.cloth.ClothGrid;
import me.skaffy.client.cloth.ClothManager;
import me.skaffy.client.cloth.ClothMeshRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
	@Inject(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void realisticWater$submitClothCape(
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int light,
		AvatarRenderState state,
		float yRot,
		float xRot,
		CallbackInfo ci
	) {
		if (state.isInvisible || !state.showCape || state.skin.cape() == null) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		Identifier capeTex = state.skin.cape().texturePath();
		double px = state.x;
		double py = state.y;
		double pz = state.z;

		float bodyYawRad = state.bodyRot * Mth.DEG_TO_RAD;
		double rightX = Math.cos(bodyYawRad);
		double rightZ = Math.sin(bodyYawRad);
		double backX = Math.sin(bodyYawRad);
		double backZ = -Math.cos(bodyYawRad);

		double shoulderY = py + (state.isCrouching ? 1.18 : 1.40);
		double shoulderCenterX = px + backX * 0.14;
		double shoulderCenterZ = pz + backZ * 0.14;
		double halfW = 0.3125;

		Vec3 topLeft = new Vec3(shoulderCenterX - rightX * halfW, shoulderY, shoulderCenterZ - rightZ * halfW);
		Vec3 topRight = new Vec3(shoulderCenterX + rightX * halfW, shoulderY, shoulderCenterZ + rightZ * halfW);
		Vec3 outward = new Vec3(backX, 0.0, backZ);

		java.util.UUID key = new java.util.UUID(Double.doubleToLongBits(capeTex.hashCode()), state.id);
		ClothGrid grid = ClothManager.cape(level, key, topLeft, topRight, outward);

		collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(capeTex), (pose, consumer) -> {
			PoseStack localStack = new PoseStack();
			localStack.last().pose().set(pose.pose());
			localStack.last().normal().set(pose.normal());

			// Cape front UV sits at [1..11, 1..17] on a 64x32 cape texture
			ClothMeshRenderer.render(
				localStack, consumer, grid,
				px, py, pz,
				1.0F / 64.0F, 1.0F / 32.0F, 11.0F / 64.0F, 17.0F / 32.0F,
				light, 0xFFFFFFFF
			);
		});

		ci.cancel();
	}
}
