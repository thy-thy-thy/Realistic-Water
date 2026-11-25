package me.skaffy.client.cloth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

public final class ClothMeshRenderer {
	private ClothMeshRenderer() {
	}

	/**
	 * Renders a double-sided subdivided cloth mesh in world-relative pose space
	 * (where the PoseStack origin has already been translated to the camera or anchor).
	 */
	public static void render(
		PoseStack poseStack,
		VertexConsumer consumer,
		ClothGrid grid,
		double originX,
		double originY,
		double originZ,
		float u0,
		float v0,
		float u1,
		float v1,
		int light,
		int color
	) {
		PoseStack.Pose pose = poseStack.last();
		int cols = grid.cols;
		int rows = grid.rows;

		for (int r = 0; r < rows - 1; r++) {
			float tv0 = Mth.lerp((float) r / (rows - 1), v0, v1);
			float tv1 = Mth.lerp((float) (r + 1) / (rows - 1), v0, v1);

			for (int c = 0; c < cols - 1; c++) {
				float tu0 = Mth.lerp((float) c / (cols - 1), u0, u1);
				float tu1 = Mth.lerp((float) (c + 1) / (cols - 1), u0, u1);

				float x00 = (float) (grid.x(c, r) - originX);
				float y00 = (float) (grid.y(c, r) - originY);
				float z00 = (float) (grid.z(c, r) - originZ);

				float x10 = (float) (grid.x(c + 1, r) - originX);
				float y10 = (float) (grid.y(c + 1, r) - originY);
				float z10 = (float) (grid.z(c + 1, r) - originZ);

				float x11 = (float) (grid.x(c + 1, r + 1) - originX);
				float y11 = (float) (grid.y(c + 1, r + 1) - originY);
				float z11 = (float) (grid.z(c + 1, r + 1) - originZ);

				float x01 = (float) (grid.x(c, r + 1) - originX);
				float y01 = (float) (grid.y(c, r + 1) - originY);
				float z01 = (float) (grid.z(c, r + 1) - originZ);

				// Front face
				emitQuad(pose, consumer,
					x00, y00, z00, tu0, tv0, grid.nx(c, r), grid.ny(c, r), grid.nz(c, r),
					x01, y01, z01, tu0, tv1, grid.nx(c, r + 1), grid.ny(c, r + 1), grid.nz(c, r + 1),
					x11, y11, z11, tu1, tv1, grid.nx(c + 1, r + 1), grid.ny(c + 1, r + 1), grid.nz(c + 1, r + 1),
					x10, y10, z10, tu1, tv0, grid.nx(c + 1, r), grid.ny(c + 1, r), grid.nz(c + 1, r),
					light, color);

				// Back face (reversed winding & inverted normal)
				emitQuad(pose, consumer,
					x10, y10, z10, tu1, tv0, -grid.nx(c + 1, r), -grid.ny(c + 1, r), -grid.nz(c + 1, r),
					x11, y11, z11, tu1, tv1, -grid.nx(c + 1, r + 1), -grid.ny(c + 1, r + 1), -grid.nz(c + 1, r + 1),
					x01, y01, z01, tu0, tv1, -grid.nx(c, r + 1), -grid.ny(c, r + 1), -grid.nz(c, r + 1),
					x00, y00, z00, tu0, tv0, -grid.nx(c, r), -grid.ny(c, r), -grid.nz(c, r),
					light, color);
			}
		}
	}

	public static void renderSprite(
		PoseStack poseStack,
		VertexConsumer consumer,
		ClothGrid grid,
		double originX,
		double originY,
		double originZ,
		TextureAtlasSprite sprite,
		int light,
		int color
	) {
		// Banner cloth face sits at UV [1..21, 1..41] on the 64x64 banner texture
		float u0 = sprite.getU(1.0F / 64.0F);
		float v0 = sprite.getV(1.0F / 64.0F);
		float u1 = sprite.getU(21.0F / 64.0F);
		float v1 = sprite.getV(41.0F / 64.0F);
		render(poseStack, consumer, grid, originX, originY, originZ, u0, v0, u1, v1, light, color);
	}

	private static void emitQuad(
		PoseStack.Pose pose,
		VertexConsumer vc,
		float x0, float y0, float z0, float u0, float v0, float nx0, float ny0, float nz0,
		float x1, float y1, float z1, float u1, float v1, float nx1, float ny1, float nz1,
		float x2, float y2, float z2, float u2, float v2, float nx2, float ny2, float nz2,
		float x3, float y3, float z3, float u3, float v3, float nx3, float ny3, float nz3,
		int light,
		int color
	) {
		vc.addVertex(pose, x0, y0, z0).setColor(color).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx0, ny0, nz0);
		vc.addVertex(pose, x1, y1, z1).setColor(color).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx1, ny1, nz1);
		vc.addVertex(pose, x2, y2, z2).setColor(color).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx2, ny2, nz2);
		vc.addVertex(pose, x3, y3, z3).setColor(color).setUv(u3, v3).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx3, ny3, nz3);
	}
}
