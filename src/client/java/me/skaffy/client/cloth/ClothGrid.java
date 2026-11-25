package me.skaffy.client.cloth;

import me.skaffy.ocean.OceanWaves;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class ClothGrid {
	public final int cols;
	public final int rows;
	public final float width;
	public final float height;

	private final float[] x, y, z;
	private final float[] px, py, pz;
	private final float[] nx, ny, nz;
	private final boolean[] pinned;
	private boolean initialized;

	public ClothGrid(int cols, int rows, float width, float height) {
		this.cols = cols;
		this.rows = rows;
		this.width = width;
		this.height = height;
		int n = cols * rows;
		this.x = new float[n];
		this.y = new float[n];
		this.z = new float[n];
		this.px = new float[n];
		this.py = new float[n];
		this.pz = new float[n];
		this.nx = new float[n];
		this.ny = new float[n];
		this.nz = new float[n];
		this.pinned = new boolean[n];
		for (int c = 0; c < cols; c++) {
			this.pinned[c] = true; // Top edge attached to pole or shoulders
		}
	}

	public int idx(int c, int r) {
		return r * this.cols + c;
	}

	public float x(int c, int r) { return this.x[idx(c, r)]; }
	public float y(int c, int r) { return this.y[idx(c, r)]; }
	public float z(int c, int r) { return this.z[idx(c, r)]; }
	public float nx(int c, int r) { return this.nx[idx(c, r)]; }
	public float ny(int c, int r) { return this.ny[idx(c, r)]; }
	public float nz(int c, int r) { return this.nz[idx(c, r)]; }

	/**
	 * Updates pinned top-edge anchors in world space and advances Verlet cloth physics.
	 */
	public void step(ClientLevel level, Vec3 topLeft, Vec3 topRight, Vec3 outwardNormal, float windScale, float waterDrag) {
		if (!this.initialized) {
			for (int r = 0; r < this.rows; r++) {
				float v = (float) r / (this.rows - 1);
				for (int c = 0; c < this.cols; c++) {
					float u = (float) c / (this.cols - 1);
					int i = idx(c, r);
					this.x[i] = this.px[i] = (float) Mth.lerp(u, topLeft.x, topRight.x);
					this.y[i] = this.py[i] = (float) Mth.lerp(u, topLeft.y, topRight.y) - v * this.height;
					this.z[i] = this.pz[i] = (float) Mth.lerp(u, topLeft.z, topRight.z);
				}
			}
			this.initialized = true;
		}

		// Pin top row to pole / shoulders
		for (int c = 0; c < this.cols; c++) {
			float u = (float) c / (this.cols - 1);
			int i = idx(c, 0);
			this.x[i] = this.px[i] = (float) Mth.lerp(u, topLeft.x, topRight.x);
			this.y[i] = this.py[i] = (float) Mth.lerp(u, topLeft.y, topRight.y);
			this.z[i] = this.pz[i] = (float) Mth.lerp(u, topLeft.z, topRight.z);
		}

		long gameTime = level.getGameTime();
		float weather = OceanWaves.weather(level);
		OceanWaves.Waves waves = OceanWaves.at(gameTime, 0.0F, weather);
		double t = gameTime / 20.0;
		double windAngle = 0.6 + 0.9 * Math.sin(Math.PI * 2.0 * gameTime / (24000.0 * 5.0));
		float baseWindX = (float) Math.cos(windAngle) * 0.018F * windScale * weather;
		float baseWindZ = (float) Math.sin(windAngle) * 0.018F * windScale * weather;

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int i = 0; i < this.x.length; i++) {
			if (this.pinned[i]) {
				continue;
			}

			float vx = this.x[i] - this.px[i];
			float vy = this.y[i] - this.py[i];
			float vz = this.z[i] - this.pz[i];

			this.px[i] = this.x[i];
			this.py[i] = this.y[i];
			this.pz[i] = this.z[i];

			pos.set(Mth.floor(this.x[i]), Mth.floor(this.y[i]), Mth.floor(this.z[i]));
			FluidState fluid = level.getFluidState(pos);
			boolean submerged = !fluid.isEmpty();

			float damping = submerged ? Mth.clamp(1.0F - 0.24F * waterDrag, 0.55F, 0.92F) : 0.965F;
			float ax = 0.0F;
			float ay = -0.016F; // Gravity per 20Hz tick
			float az = 0.0F;

			if (submerged) {
				// Slight neutral buoyancy + ocean wave orbital velocity push
				ay += 0.0135F;
				double[] waveVel = waves.velocity(this.x[i], this.z[i], -0.5);
				Vec3 flow = fluid.getFlow(level, pos);
				ax += (float) (waveVel[0] * 0.012 + flow.x * 0.025) * waterDrag;
				ay += (float) (waveVel[1] * 0.010) * waterDrag;
				az += (float) (waveVel[2] * 0.012 + flow.z * 0.025) * waterDrag;
			} else {
				// Gusty turbulent wind flutter
				float gust = 0.65F + 0.55F * (float) Math.sin(t * 3.1 + this.x[i] * 0.7 + this.z[i] * 0.5)
					+ 0.25F * (float) Math.cos(t * 7.4 + this.y[i] * 1.9);
				ax += baseWindX * gust;
				az += baseWindZ * gust;
				ay += Math.abs(baseWindX + baseWindZ) * 0.15F * (float) Math.sin(t * 5.2 + this.x[i]);
			}

			this.x[i] += vx * damping + ax;
			this.y[i] += vy * damping + ay;
			this.z[i] += vz * damping + az;
		}

		// Jakobsen distance constraints (5 iterations for crisp cloth stiffness)
		float restX = this.width / (this.cols - 1);
		float restY = this.height / (this.rows - 1);
		float restDiag = Mth.sqrt(restX * restX + restY * restY);

		for (int iter = 0; iter < 5; iter++) {
			for (int r = 0; r < this.rows; r++) {
				for (int c = 0; c < this.cols; c++) {
					int a = idx(c, r);
					if (c + 1 < this.cols) {
						satisfy(a, idx(c + 1, r), restX);
					}
					if (r + 1 < this.rows) {
						satisfy(a, idx(c, r + 1), restY);
					}
					if (c + 1 < this.cols && r + 1 < this.rows) {
						satisfy(a, idx(c + 1, r + 1), restDiag);
						satisfy(idx(c + 1, r), idx(c, r + 1), restDiag);
					}
				}
			}
			// Keep cloth from clipping backward through the banner pole or player back
			if (outwardNormal.lengthSqr() > 1e-4) {
				for (int r = 1; r < this.rows; r++) {
					for (int c = 0; c < this.cols; c++) {
						int i = idx(c, r);
						float u = (float) c / (this.cols - 1);
						double ax = Mth.lerp(u, topLeft.x, topRight.x);
						double az = Mth.lerp(u, topLeft.z, topRight.z);
						double dot = (this.x[i] - ax) * outwardNormal.x + (this.z[i] - az) * outwardNormal.z;
						if (dot < -0.04) {
							double push = -0.04 - dot;
							this.x[i] += (float) (outwardNormal.x * push);
							this.z[i] += (float) (outwardNormal.z * push);
						}
					}
				}
			}
		}

		this.recomputeNormals();
	}

	private void satisfy(int a, int b, float rest) {
		float dx = this.x[b] - this.x[a];
		float dy = this.y[b] - this.y[a];
		float dz = this.z[b] - this.z[a];
		float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist < 1e-5F) {
			return;
		}
		float diff = (dist - rest) / dist;
		if (!this.pinned[a] && !this.pinned[b]) {
			float half = 0.5F * diff;
			this.x[a] += dx * half; this.y[a] += dy * half; this.z[a] += dz * half;
			this.x[b] -= dx * half; this.y[b] -= dy * half; this.z[b] -= dz * half;
		} else if (!this.pinned[a]) {
			this.x[a] += dx * diff; this.y[a] += dy * diff; this.z[a] += dz * diff;
		} else if (!this.pinned[b]) {
			this.x[b] -= dx * diff; this.y[b] -= dy * diff; this.z[b] -= dz * diff;
		}
	}

	private void recomputeNormals() {
		for (int r = 0; r < this.rows; r++) {
			for (int c = 0; c < this.cols; c++) {
				int c0 = Math.max(0, c - 1), c1 = Math.min(this.cols - 1, c + 1);
				int r0 = Math.max(0, r - 1), r1 = Math.min(this.rows - 1, r + 1);
				int iL = idx(c0, r), iR = idx(c1, r);
				int iU = idx(c, r0), iD = idx(c, r1);

				float ux = this.x[iR] - this.x[iL], uy = this.y[iR] - this.y[iL], uz = this.z[iR] - this.z[iL];
				float vx = this.x[iD] - this.x[iU], vy = this.y[iD] - this.y[iU], vz = this.z[iD] - this.z[iU];

				float cx = uy * vz - uz * vy;
				float cy = uz * vx - ux * vz;
				float cz = ux * vy - uy * vx;
				float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
				int i = idx(c, r);
				if (len > 1e-5F) {
					this.nx[i] = cx / len;
					this.ny[i] = cy / len;
					this.nz[i] = cz / len;
				} else {
					this.nx[i] = 0.0F;
					this.ny[i] = 0.0F;
					this.nz[i] = 1.0F;
				}
			}
		}
	}
}
