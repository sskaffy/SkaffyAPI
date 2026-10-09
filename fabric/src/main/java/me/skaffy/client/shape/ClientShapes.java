package me.skaffy.client.shape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.commands.RenderPass;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.mixin.PreparedFrameAccessor;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.shapes.ShapesCodec;
import me.skaffy.protocol.shapes.ShapesPacket;
import me.skaffy.protocol.shapes.ShapesPacket.Box;
import me.skaffy.protocol.shapes.ShapesPacket.ClearShapes;
import me.skaffy.protocol.shapes.ShapesPacket.Line;
import me.skaffy.protocol.shapes.ShapesPacket.Mode;
import me.skaffy.protocol.shapes.ShapesPacket.MoveShape;
import me.skaffy.protocol.shapes.ShapesPacket.Part;
import me.skaffy.protocol.shapes.ShapesPacket.Placement;
import me.skaffy.protocol.shapes.ShapesPacket.Quad;
import me.skaffy.protocol.shapes.ShapesPacket.RemoveShape;
import me.skaffy.protocol.shapes.ShapesPacket.SetShape;
import me.skaffy.protocol.shapes.ShapesPacket.Style;
import me.skaffy.protocol.shapes.ShapesPacket.Triangle;
import me.skaffy.protocol.shapes.ShapesPacket.Vertex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class ClientShapes {
	private static final Map<String, Shape> SHAPES = new LinkedHashMap<>();
	private static boolean overlays;

	public interface Storage {
		SimpleFeatureRenderPhase skaffy$lateShapes();
	}

	private record Face(float[] corners, float[] uvs, int argb, float nx, float ny, float nz, boolean box) {
		float cx() {
			return (corners[0] + corners[3] + corners[6] + corners[9]) / 4;
		}

		float cy() {
			return (corners[1] + corners[4] + corners[7] + corners[10]) / 4;
		}

		float cz() {
			return (corners[2] + corners[5] + corners[8] + corners[11]) / 4;
		}
	}

	private record Segment(float ax, float ay, float az, float bx, float by, float bz, int argb, float width) {
	}

	private static final class Shape {
		Placement placement;
		final Style style;
		final List<Face> faces;
		final List<Segment> segments;
		final float reach;

		Shape(Placement placement, Style style, List<Face> faces, List<Segment> segments, float reach) {
			this.placement = placement;
			this.style = style;
			this.faces = faces;
			this.segments = segments;
			this.reach = reach;
		}
	}

	private ClientShapes() {
	}


	public static void receive(byte[] data) {
		ShapesPacket packet;

		try {
			packet = ShapesCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed shapes packet: {}", e.getMessage());
			return;
		}

		Shape compiled = packet instanceof SetShape set ? compile(set) : null;
		Minecraft.getInstance().execute(() -> apply(packet, compiled));
	}

	private static void apply(ShapesPacket packet, @Nullable Shape compiled) {
		switch (packet) {
			case SetShape set -> {
				if (!SHAPES.containsKey(set.id()) && SHAPES.size() >= ShapesCodec.MAX_SHAPES) {
					SkaffySAPIClient.LOGGER.warn("The server sent more than {} shapes, {} is ignored", ShapesCodec.MAX_SHAPES, set.id());
					return;
				}

				SHAPES.put(set.id(), compiled);
			}
			case MoveShape move -> {
				Shape shape = SHAPES.get(move.id());

				if (shape != null) {
					shape.placement = move.placement();
				}
			}
			case RemoveShape remove -> SHAPES.remove(remove.id());
			case ClearShapes clear -> SHAPES.keySet().removeIf(id -> id.startsWith(clear.prefix()));
		}
	}

	private static Shape compile(SetShape set) {
		List<Face> faces = new ArrayList<>();
		List<Segment> segments = new ArrayList<>();
		float reach = 0;
		boolean world = set.style().mode() == Mode.WORLD;

		for (Part part : set.parts()) {
			switch (part) {
				case Line line -> {
					if (!world) {
						segments.add(new Segment(line.ax(), line.ay(), line.az(), line.bx(), line.by(), line.bz(), line.argb(), line.width()));
					}

					reach = Math.max(reach, Math.max(length(line.ax(), line.ay(), line.az()), length(line.bx(), line.by(), line.bz())));
				}
				case Triangle triangle -> {
					faces.add(face(new Vertex[] {triangle.a(), triangle.b(), triangle.c(), triangle.c()}, triangle.argb(), false));
					reach = Math.max(reach, reach(triangle.a(), triangle.b(), triangle.c()));
				}
				case Quad quad -> {
					faces.add(face(new Vertex[] {quad.a(), quad.b(), quad.c(), quad.d()}, quad.argb(), false));
					reach = Math.max(reach, reach(quad.a(), quad.b(), quad.c(), quad.d()));
				}
				case Box box -> {
					if ((box.fill() >>> 24) != 0) {
						boxFaces(box, faces);
					}

					if (!world && (box.outline() >>> 24) != 0) {
						boxEdges(box, segments);
					}

					reach = Math.max(reach, Math.max(length(box.minX(), box.minY(), box.minZ()), length(box.maxX(), box.maxY(), box.maxZ())));
					reach = Math.max(reach, Math.max(length(box.minX(), box.maxY(), box.maxZ()), length(box.maxX(), box.minY(), box.minZ())));
				}
			}
		}

		return new Shape(set.placement(), set.style(), List.copyOf(faces), List.copyOf(segments), reach);
	}

	private static float length(float x, float y, float z) {
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	private static float reach(Vertex... vertices) {
		float reach = 0;

		for (Vertex vertex : vertices) {
			reach = Math.max(reach, length(vertex.x(), vertex.y(), vertex.z()));
		}

		return reach;
	}

	private static Face face(Vertex[] corners, int argb, boolean box) {
		float[] positions = new float[12];
		float[] uvs = new float[8];

		for (int i = 0; i < 4; i++) {
			positions[i * 3] = corners[i].x();
			positions[i * 3 + 1] = corners[i].y();
			positions[i * 3 + 2] = corners[i].z();
			uvs[i * 2] = corners[i].u();
			uvs[i * 2 + 1] = corners[i].v();
		}

		Vector3f normal = new Vector3f(positions[3] - positions[0], positions[4] - positions[1], positions[5] - positions[2])
				.cross(positions[6] - positions[0], positions[7] - positions[1], positions[8] - positions[2]);

		if (normal.lengthSquared() < 1e-12f) {
			normal.set(0, 1, 0);
		}

		normal.normalize();
		return new Face(positions, uvs, argb, normal.x, normal.y, normal.z, box);
	}

	private static void boxFaces(Box box, List<Face> faces) {
		float x0 = box.minX();
		float y0 = box.minY();
		float z0 = box.minZ();
		float x1 = box.maxX();
		float y1 = box.maxY();
		float z1 = box.maxZ();
		float scale = box.uvScale();
		int color = box.fill();
		faces.add(boxFace(new float[] {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, 2, 1, color, scale, z1, z0, y1, y0, true));
		faces.add(boxFace(new float[] {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, 2, 1, color, scale, z0, z1, y1, y0, false));
		faces.add(boxFace(new float[] {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, 0, 2, color, scale, x0, x1, z1, z0, false));
		faces.add(boxFace(new float[] {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, 0, 2, color, scale, x0, x1, z0, z1, false));
		faces.add(boxFace(new float[] {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, 0, 1, color, scale, x0, x1, y1, y0, false));
		faces.add(boxFace(new float[] {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, 0, 1, color, scale, x1, x0, y1, y0, true));
	}

	private static Face boxFace(float[] corners, int uAxis, int vAxis, int argb, float scale, float uStart, float uEnd, float vTop, float vBottom, boolean flipU) {
		float[] uvs = new float[8];

		for (int i = 0; i < 4; i++) {
			float u = corners[i * 3 + uAxis];
			float v = corners[i * 3 + vAxis];

			if (scale > 0) {
				uvs[i * 2] = (flipU ? -u : u) / scale;
				uvs[i * 2 + 1] = -v / scale;
			} else {
				uvs[i * 2] = uEnd == uStart ? 0 : (u - uStart) / (uEnd - uStart);
				uvs[i * 2 + 1] = vTop == vBottom ? 0 : (vTop - v) / (vTop - vBottom);
			}
		}

		Vector3f normal = new Vector3f(corners[3] - corners[0], corners[4] - corners[1], corners[5] - corners[2])
				.cross(corners[6] - corners[0], corners[7] - corners[1], corners[8] - corners[2]);

		if (normal.lengthSquared() < 1e-12f) {
			normal.set(0, 1, 0);
		}

		normal.normalize();
		return new Face(corners, uvs, argb, normal.x, normal.y, normal.z, true);
	}

	private static void boxEdges(Box box, List<Segment> segments) {
		float[] x = {box.minX(), box.maxX()};
		float[] y = {box.minY(), box.maxY()};
		float[] z = {box.minZ(), box.maxZ()};
		int color = box.outline();
		float width = box.outlineWidth();

		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				segments.add(new Segment(x[0], y[i], z[j], x[1], y[i], z[j], color, width));
				segments.add(new Segment(x[i], y[0], z[j], x[i], y[1], z[j], color, width));
				segments.add(new Segment(x[i], y[j], z[0], x[i], y[j], z[1], color, width));
			}
		}
	}


	public static void submit(SubmitNodeCollector collector, CameraRenderState camera) {
		overlays = false;
		ClientLevel level = Minecraft.getInstance().level;

		if (SHAPES.isEmpty() || level == null || !camera.initialized) {
			return;
		}

		String dimension = level.dimension().identifier().toString();
		double renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0;
		double camX = camera.pos.x();
		double camY = camera.pos.y();
		double camZ = camera.pos.z();
		PoseStack poseStack = new PoseStack();

		for (Iterator<Shape> it = SHAPES.values().iterator(); it.hasNext(); ) {
			Shape shape = it.next();
			Placement at = shape.placement;

			if (!at.dimension().equals(dimension)) {
				continue;
			}

			float scale = Math.max(Math.abs(at.sx()), Math.max(Math.abs(at.sy()), Math.abs(at.sz())));
			double distance = Math.sqrt((at.x() - camX) * (at.x() - camX) + (at.y() - camY) * (at.y() - camY) + (at.z() - camZ) * (at.z() - camZ)) - shape.reach * scale;
			double limit = shape.style.viewDistance() > 0 ? shape.style.viewDistance() : renderDistance;

			if (distance > limit) {
				continue;
			}

			Quaternionf rotation = new Quaternionf(at.qx(), at.qy(), at.qz(), at.qw());

			if (rotation.lengthSquared() < 1e-12f) {
				rotation.identity();
			}

			rotation.normalize();
			poseStack.pushPose();
			poseStack.translate((float) (at.x() - camX), (float) (at.y() - camY), (float) (at.z() - camZ));
			poseStack.last().rotate(rotation);
			poseStack.scale(at.sx(), at.sy(), at.sz());

			if (shape.style.mode() == Mode.WORLD) {
				submitWorld(collector, poseStack, shape, level, rotation);
			} else {
				submitOverlay(collector, poseStack, shape);
			}

			poseStack.popPose();
		}
	}

	private static void submitWorld(SubmitNodeCollector collector, PoseStack poseStack, Shape shape, ClientLevel level, Quaternionf rotation) {
		if (shape.faces.isEmpty()) {
			return;
		}

		Placement at = shape.placement;
		int[] lights = new int[shape.faces.size()];
		Vector3f point = new Vector3f();
		Vector3f normal = new Vector3f();

		for (int i = 0; i < lights.length; i++) {
			Face face = shape.faces.get(i);

			if (shape.style.emissive()) {
				lights[i] = LightCoordsUtil.FULL_BRIGHT;
				continue;
			}

			rotation.transform(point.set(face.cx() * at.sx(), face.cy() * at.sy(), face.cz() * at.sz()));
			rotation.transform(normal.set(face.nx() / nonZero(at.sx()), face.ny() / nonZero(at.sy()), face.nz() / nonZero(at.sz()))).normalize();
			lights[i] = LightCoordsUtil.getLightCoords(level, BlockPos.containing(at.x() + point.x + normal.x * 0.4, at.y() + point.y + normal.y * 0.4, at.z() + point.z + normal.z * 0.4));
		}

		RenderType type = ShapeRenderTypes.world(ShapeTextures.get(shape.style.texture()), shape.style.render());
		boolean doubleSided = shape.style.doubleSided();
		collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
			for (int i = 0; i < shape.faces.size(); i++) {
				Face face = shape.faces.get(i);
				worldFace(pose, consumer, face, lights[i], false);

				if (doubleSided && !face.box()) {
					worldFace(pose, consumer, face, lights[i], true);
				}
			}
		});
	}

	private static float nonZero(float value) {
		return Math.abs(value) < 1e-6f ? 1e-6f : value;
	}

	private static void worldFace(PoseStack.Pose pose, VertexConsumer consumer, Face face, int light, boolean back) {
		float sign = back ? -1 : 1;

		for (int k = 0; k < 4; k++) {
			int i = back ? 3 - k : k;
			consumer.addVertex(pose, face.corners()[i * 3], face.corners()[i * 3 + 1], face.corners()[i * 3 + 2])
					.setColor(face.argb())
					.setUv(face.uvs()[i * 2], face.uvs()[i * 2 + 1])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, face.nx() * sign, face.ny() * sign, face.nz() * sign);
		}
	}

	private static void submitOverlay(SubmitNodeCollector collector, PoseStack poseStack, Shape shape) {
		if (!(collector instanceof Storage storage)) {
			return;
		}

		boolean through = shape.style.seeThrough();
		overlays |= !shape.faces.isEmpty() || !shape.segments.isEmpty();

		if (!shape.faces.isEmpty()) {
			storage.skaffy$lateShapes().submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), through ? ShapeRenderTypes.FILL_THROUGH : ShapeRenderTypes.FILL,
					(pose, consumer) -> {
						for (Face face : shape.faces) {
							for (int i = 0; i < 4; i++) {
								consumer.addVertex(pose, face.corners()[i * 3], face.corners()[i * 3 + 1], face.corners()[i * 3 + 2]).setColor(face.argb());
							}
						}
					}));
		}

		if (!shape.segments.isEmpty()) {
			storage.skaffy$lateShapes().submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), through ? ShapeRenderTypes.LINES_THROUGH : ShapeRenderTypes.LINES,
					(pose, consumer) -> lines(pose, consumer, shape.segments)));
		}
	}

	private static void lines(PoseStack.Pose pose, VertexConsumer consumer, List<Segment> segments) {
		Matrix4f view = RenderSystem.getModelViewMatrixCopy();
		PoseStack.Pose flat = new PoseStack().last();
		Vector4f start = new Vector4f();
		Vector4f end = new Vector4f();
		Vector4f startView = new Vector4f();
		Vector4f endView = new Vector4f();

		for (Segment segment : segments) {
			pose.pose().transform(start.set(segment.ax(), segment.ay(), segment.az(), 1));
			pose.pose().transform(end.set(segment.bx(), segment.by(), segment.bz(), 1));
			start.mul(view, startView);
			end.mul(view, endView);
			boolean startBehind = startView.z > -0.05f;
			boolean endBehind = endView.z > -0.05f;

			if (startBehind && endBehind) {
				continue;
			}

			if (startBehind || endBehind) {
				float denominator = endView.z - startView.z;

				if (Math.abs(denominator) < 1e-9f) {
					continue;
				}

				float t = Math.clamp((-0.05f - startView.z) / denominator, 0, 1);
				Vector4f cut = new Vector4f(start).lerp(end, t);

				if (startBehind) {
					start.set(cut);
				} else {
					end.set(cut);
				}
			}

			float dx = end.x - start.x;
			float dy = end.y - start.y;
			float dz = end.z - start.z;
			consumer.addVertex(flat, start.x, start.y, start.z).setNormal(flat, dx, dy, dz).setColor(segment.argb()).setLineWidth(segment.width());
			consumer.addVertex(flat, end.x, end.y, end.z).setNormal(flat, dx, dy, dz).setColor(segment.argb()).setLineWidth(segment.width());
		}
	}

	public static boolean hasOverlays() {
		return overlays;
	}

	public static void executeLate(FeatureRenderDispatcher.PreparedFrame frame, RenderPass renderPass) {
		PreparedFrameAccessor accessor = (PreparedFrameAccessor) frame;
		FeatureFrameContext context = accessor.skaffy$context();

		if (accessor.skaffy$submitNodeStorage() instanceof Storage storage && context != null) {
			accessor.skaffy$executePhase(storage.skaffy$lateShapes(), context, renderPass);
		}
	}


	public static void load(DiskAssetStore store) {
		Minecraft.getInstance().execute(() -> ShapeTextures.assets(store));
	}

	public static void unload() {
		Minecraft.getInstance().execute(() -> {
			SHAPES.clear();
			ShapeTextures.clear();
			ShapeTextures.assets(null);
		});
	}
}
