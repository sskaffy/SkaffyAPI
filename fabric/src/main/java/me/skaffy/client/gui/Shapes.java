package me.skaffy.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import me.skaffy.client.gui.element.Affine;
import me.skaffy.client.mixin.GuiGraphicsExtractorAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

final class Shapes {
	private static final Matrix3x2f IDENTITY = new Matrix3x2f();
	private static final RenderPipeline GUI = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skaffys-api", "pipeline/gui_shapes"))
			.withCull(false)
			.build());
	private static final RenderPipeline GUI_TEXTURED = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skaffys-api", "pipeline/gui_shapes_textured"))
			.withCull(false)
			.build());
	private static final RenderPipeline GUI_INVERT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skaffys-api", "pipeline/gui_shapes_invert"))
			.withColorTargetState(new ColorTargetState(BlendFunction.INVERT))
			.withCull(false)
			.build());
	private static final RenderPipeline GUI_TEXTURED_INVERT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skaffys-api", "pipeline/gui_shapes_textured_invert"))
			.withColorTargetState(new ColorTargetState(BlendFunction.INVERT))
			.withCull(false)
			.build());
	static boolean invert;

	private Shapes() {
	}

	static double pixel() {
		return 1.0 / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}


	private static final class Mesh {
		float[] data = new float[256];
		int[] colors = new int[64];
		int count;

		void vertex(double x, double y, double u, double v, int color) {
			if (count * 4 + 4 > data.length) {
				data = java.util.Arrays.copyOf(data, data.length * 2);
				colors = java.util.Arrays.copyOf(colors, colors.length * 2);
			}

			data[count * 4] = (float) x;
			data[count * 4 + 1] = (float) y;
			data[count * 4 + 2] = (float) u;
			data[count * 4 + 3] = (float) v;
			colors[count] = color;
			count++;
		}
	}

	private record ShapeState(RenderPipeline pipeline, TextureSetup textureSetup, float[] data, int[] colors, int count, boolean textured,
			@Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			for (int i = 0; i < count; i++) {
				VertexConsumer vertex = consumer.addVertexWith2DPose(IDENTITY, data[i * 4], data[i * 4 + 1]);

				if (textured) {
					vertex = vertex.setUv(data[i * 4 + 2], data[i * 4 + 3]);
				}

				vertex.setColor(colors[i]);
			}
		}
	}

	private static void submit(GuiGraphicsExtractor graphics, Mesh mesh, GuiTextures.@Nullable Texture texture) {
		if (mesh.count == 0) {
			return;
		}

		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;

		for (int i = 0; i < mesh.count; i++) {
			minX = Math.min(minX, mesh.data[i * 4]);
			maxX = Math.max(maxX, mesh.data[i * 4]);
			minY = Math.min(minY, mesh.data[i * 4 + 1]);
			maxY = Math.max(maxY, mesh.data[i * 4 + 1]);
		}

		if (!(minX <= maxX) || Float.isInfinite(minX) || Float.isInfinite(maxX)) {
			return;
		}

		int x0 = (int) Math.floor(minX);
		int y0 = (int) Math.floor(minY);
		ScreenRectangle bounds = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0));
		ScreenRectangle scissor = ((GuiGraphicsExtractorAccessor) graphics).skaffy$scissorStack().peek();

		if (scissor != null) {
			bounds = scissor.intersection(bounds);

			if (bounds == null) {
				return;
			}
		}

		boolean textured = texture != null;
		RenderPipeline pipeline = textured ? invert ? GUI_TEXTURED_INVERT : GUI_TEXTURED : invert ? GUI_INVERT : GUI;
		TextureSetup setup = textured ? texture.setup() : TextureSetup.noTexture();
		graphics.guiRenderState.addGuiElement(new ShapeState(pipeline, setup, java.util.Arrays.copyOf(mesh.data, mesh.count * 4),
				java.util.Arrays.copyOf(mesh.colors, mesh.count), mesh.count, textured, scissor, bounds));
	}


	private static double[] fitRadii(double width, double height, double @Nullable [] radii) {
		if (radii == null) {
			return new double[4];
		}

		double tl = Math.max(0, radii[0]);
		double tr = Math.max(0, radii[1]);
		double br = Math.max(0, radii[2]);
		double bl = Math.max(0, radii[3]);
		double factor = 1;

		if (tl + tr > width) {
			factor = Math.min(factor, width / (tl + tr));
		}

		if (bl + br > width) {
			factor = Math.min(factor, width / (bl + br));
		}

		if (tl + bl > height) {
			factor = Math.min(factor, height / (tl + bl));
		}

		if (tr + br > height) {
			factor = Math.min(factor, height / (tr + br));
		}

		return new double[] {tl * factor, tr * factor, br * factor, bl * factor};
	}

	private static int segments(double radius, double screenScale) {
		if (radius <= 0) {
			return 0;
		}

		return Math.clamp((int) Math.ceil(Math.sqrt(radius * screenScale) * 2.2), 2, 48);
	}

	private static double[] outline(double x, double y, double width, double height, double[] radii, int @Nullable [] counts, double screenScale) {
		double[][] centers = {
				{x + radii[0], y + radii[0]},
				{x + width - radii[1], y + radii[1]},
				{x + width - radii[2], y + height - radii[2]},
				{x + radii[3], y + height - radii[3]}};
		double[] starts = {Math.PI, Math.PI * 1.5, 0, Math.PI * 0.5};
		double[][] corners = {{x, y}, {x + width, y}, {x + width, y + height}, {x, y + height}};
		int total = 0;
		int[] used = new int[4];

		for (int corner = 0; corner < 4; corner++) {
			used[corner] = counts != null ? counts[corner] : segments(radii[corner], screenScale);
			total += used[corner] + 1;
		}

		double[] points = new double[total * 2];
		int index = 0;

		for (int corner = 0; corner < 4; corner++) {
			int n = used[corner];

			if (n == 0 || radii[corner] <= 0) {
				for (int k = 0; k <= n; k++) {
					points[index++] = corners[corner][0];
					points[index++] = corners[corner][1];
				}

				continue;
			}

			for (int k = 0; k <= n; k++) {
				double angle = starts[corner] + k / (double) n * Math.PI / 2;
				points[index++] = centers[corner][0] + radii[corner] * Math.cos(angle);
				points[index++] = centers[corner][1] + radii[corner] * Math.sin(angle);
			}
		}

		return points;
	}

	private static double[] pixelOutline(double x, double y, double width, double height, double[] radii) {
		java.util.List<double[]> points = new java.util.ArrayList<>();
		int[] r = new int[4];

		for (int i = 0; i < 4; i++) {
			r[i] = (int) Math.round(radii[i]);
		}

		for (int row = r[0] - 1; row >= 0; row--) {
			double inset = inset(r[0], row);
			points.add(new double[] {x + inset, y + row + 1});
			points.add(new double[] {x + inset, y + row});
		}

		if (r[0] == 0) {
			points.add(new double[] {x, y});
		}

		for (int row = 0; row < r[1]; row++) {
			double inset = inset(r[1], row);
			points.add(new double[] {x + width - inset, y + row});
			points.add(new double[] {x + width - inset, y + row + 1});
		}

		if (r[1] == 0) {
			points.add(new double[] {x + width, y});
		}

		for (int row = r[2] - 1; row >= 0; row--) {
			double inset = inset(r[2], row);
			points.add(new double[] {x + width - inset, y + height - row - 1});
			points.add(new double[] {x + width - inset, y + height - row});
		}

		if (r[2] == 0) {
			points.add(new double[] {x + width, y + height});
		}

		for (int row = 0; row < r[3]; row++) {
			double inset = inset(r[3], row);
			points.add(new double[] {x + inset, y + height - row});
			points.add(new double[] {x + inset, y + height - row - 1});
		}

		if (r[3] == 0) {
			points.add(new double[] {x, y + height});
		}

		double[] result = new double[points.size() * 2];

		for (int i = 0; i < points.size(); i++) {
			result[i * 2] = points.get(i)[0];
			result[i * 2 + 1] = points.get(i)[1];
		}

		return result;
	}

	private static double inset(int radius, int row) {
		double dy = radius - row - 0.5;
		return Math.round(radius - Math.sqrt(Math.max(0, radius * (double) radius - dy * dy)));
	}

	private static double[] toScreen(Affine matrix, double[] local) {
		double[] screen = new double[local.length];

		for (int i = 0; i < local.length; i += 2) {
			screen[i] = matrix.x(local[i], local[i + 1]);
			screen[i + 1] = matrix.y(local[i], local[i + 1]);
		}

		return screen;
	}

	private static double[] normals(double[] points) {
		int n = points.length / 2;
		double area = 0;

		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1];
		}

		double sign = area >= 0 ? 1 : -1;
		double[] edges = new double[n * 2];

		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			double dx = points[j * 2] - points[i * 2];
			double dy = points[j * 2 + 1] - points[i * 2 + 1];
			double length = Math.hypot(dx, dy);

			if (length > 1e-6) {
				edges[i * 2] = dy / length * sign;
				edges[i * 2 + 1] = -dx / length * sign;
			}
		}

		double[] result = new double[n * 2];

		for (int i = 0; i < n; i++) {
			double[] previous = neighbour(edges, i, -1);
			double[] next = neighbour(edges, i, 0);
			double nx = previous[0] + next[0];
			double ny = previous[1] + next[1];
			double length = Math.hypot(nx, ny);

			if (length < 1e-6) {
				nx = next[0];
				ny = next[1];
				length = Math.max(1e-6, Math.hypot(nx, ny));
			}

			nx /= length;
			ny /= length;
			double cos = nx * next[0] + ny * next[1];
			double miter = 1 / Math.max(0.35, cos);
			result[i * 2] = nx * miter;
			result[i * 2 + 1] = ny * miter;
		}

		return result;
	}

	private static double[] neighbour(double[] edges, int vertex, int direction) {
		int n = edges.length / 2;

		for (int step = 0; step < n; step++) {
			int edge = Math.floorMod(direction < 0 ? vertex - 1 - step : vertex + step, n);

			if (edges[edge * 2] != 0 || edges[edge * 2 + 1] != 0) {
				return new double[] {edges[edge * 2], edges[edge * 2 + 1]};
			}
		}

		return new double[] {0, 0};
	}

	private static int clearOf(int color) {
		return color & 0xFFFFFF;
	}

	private static int withOpacity(int color, double opacity) {
		int alpha = (int) Math.round((color >>> 24) * Math.clamp(opacity, 0, 1));
		return alpha << 24 | color & 0xFFFFFF;
	}

	private static void fill(Mesh mesh, double[] points, double @Nullable [] uv, int color, double fringe) {
		int n = points.length / 2;

		if (n < 3) {
			return;
		}

		double cx = 0;
		double cy = 0;
		double cu = 0;
		double cv = 0;

		for (int i = 0; i < n; i++) {
			cx += points[i * 2];
			cy += points[i * 2 + 1];

			if (uv != null) {
				cu += uv[i * 2];
				cv += uv[i * 2 + 1];
			}
		}

		cx /= n;
		cy /= n;
		cu /= n;
		cv /= n;

		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			mesh.vertex(cx, cy, cu, cv, color);
			mesh.vertex(points[i * 2], points[i * 2 + 1], uv == null ? 0 : uv[i * 2], uv == null ? 0 : uv[i * 2 + 1], color);
			mesh.vertex(points[j * 2], points[j * 2 + 1], uv == null ? 0 : uv[j * 2], uv == null ? 0 : uv[j * 2 + 1], color);
			mesh.vertex(points[j * 2], points[j * 2 + 1], uv == null ? 0 : uv[j * 2], uv == null ? 0 : uv[j * 2 + 1], color);
		}

		if (fringe > 0) {
			edge(mesh, points, uv, normals(points), fringe, color, clearOf(color));
		}
	}

	private static void edge(Mesh mesh, double[] points, double @Nullable [] uv, double[] normals, double width, int inner, int outer) {
		int n = points.length / 2;

		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			double ui = uv == null ? 0 : uv[i * 2];
			double vi = uv == null ? 0 : uv[i * 2 + 1];
			double uj = uv == null ? 0 : uv[j * 2];
			double vj = uv == null ? 0 : uv[j * 2 + 1];
			mesh.vertex(points[i * 2], points[i * 2 + 1], ui, vi, inner);
			mesh.vertex(points[j * 2], points[j * 2 + 1], uj, vj, inner);
			mesh.vertex(points[j * 2] + normals[j * 2] * width, points[j * 2 + 1] + normals[j * 2 + 1] * width, uj, vj, outer);
			mesh.vertex(points[i * 2] + normals[i * 2] * width, points[i * 2 + 1] + normals[i * 2 + 1] * width, ui, vi, outer);
		}
	}


	static void box(GuiGraphicsExtractor graphics, Affine matrix, double width, double height, double[] radii, boolean pixelCorners,
			int fillColor, int @Nullable [] gradient, double gradientAngle, double borderWidth, int borderColor, double opacity) {
		if (width <= 0 || height <= 0) {
			return;
		}

		double[] fitted = fitRadii(width, height, radii);
		double scale = matrix.scaleFactor() / pixel();
		double fringe = pixelCorners ? 0 : pixel();
		boolean hasBorder = borderWidth > 0 && (borderColor >>> 24) > 0;
		double inset = hasBorder ? Math.min(borderWidth / 2, Math.min(width, height) / 2) : 0;
		boolean hasFill = gradient != null ? gradient.length > 0 : (fillColor >>> 24) > 0;

		if (hasFill) {
			double[] fillRadii = inset(fitted, inset);
			double[] local = pixelCorners ? pixelOutline(inset, inset, width - inset * 2, height - inset * 2, fillRadii)
					: outline(inset, inset, width - inset * 2, height - inset * 2, fillRadii, null, scale);
			double[] screen = toScreen(matrix, local);
			Mesh mesh = new Mesh();

			if (gradient != null) {
				GuiTextures.Texture texture = GuiTextures.gradient(gradient);
				double[] uv = new double[local.length];
				double radians = Math.toRadians(gradientAngle);
				double dx = Math.sin(radians);
				double dy = -Math.cos(radians);
				double length = Math.abs(width * dx) + Math.abs(height * dy);

				for (int i = 0; i < local.length; i += 2) {
					double t = ((local[i] - width / 2) * dx + (local[i + 1] - height / 2) * dy) / Math.max(1e-6, length) + 0.5;
					uv[i] = Math.clamp(t, 0.002, 0.998);
					uv[i + 1] = 0.5;
				}

				fill(mesh, screen, uv, withOpacity(0xFFFFFFFF, opacity), fringe);
				submit(graphics, mesh, texture);
			} else {
				fill(mesh, screen, null, withOpacity(fillColor, opacity), fringe);
				submit(graphics, mesh, null);
			}
		}

		if (hasBorder) {
			border(graphics, matrix, width, height, fitted, pixelCorners, borderWidth, withOpacity(borderColor, opacity), scale);
		}
	}

	private static double[] inset(double[] radii, double by) {
		return new double[] {Math.max(0, radii[0] - by), Math.max(0, radii[1] - by), Math.max(0, radii[2] - by), Math.max(0, radii[3] - by)};
	}

	private static void border(GuiGraphicsExtractor graphics, Affine matrix, double width, double height, double[] radii, boolean pixelCorners, double borderWidth, int color, double scale) {
		double bw = Math.min(borderWidth, Math.min(width, height) / 2);
		Mesh mesh = new Mesh();

		if (bw * 2 >= Math.min(width, height) - 1e-6) {
			double[] outer = toScreen(matrix, pixelCorners ? pixelOutline(0, 0, width, height, radii) : outline(0, 0, width, height, radii, null, scale));
			fill(mesh, outer, null, color, pixelCorners ? 0 : pixel());
			submit(graphics, mesh, null);
			return;
		}

		double[] innerRadii = inset(radii, bw);
		double[] outerLocal;
		double[] innerLocal;

		if (pixelCorners) {
			pixelBorder(graphics, matrix, width, height, radii, bw, color);
			return;
		}

		int[] counts = new int[4];

		for (int i = 0; i < 4; i++) {
			counts[i] = segments(radii[i], scale);
		}

		outerLocal = outline(0, 0, width, height, radii, counts, scale);
		innerLocal = outline(bw, bw, width - bw * 2, height - bw * 2, innerRadii, counts, scale);
		double[] outer = toScreen(matrix, outerLocal);
		double[] inner = toScreen(matrix, innerLocal);
		int n = outer.length / 2;

		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			mesh.vertex(outer[i * 2], outer[i * 2 + 1], 0, 0, color);
			mesh.vertex(outer[j * 2], outer[j * 2 + 1], 0, 0, color);
			mesh.vertex(inner[j * 2], inner[j * 2 + 1], 0, 0, color);
			mesh.vertex(inner[i * 2], inner[i * 2 + 1], 0, 0, color);
		}

		double fringe = pixel();
		edge(mesh, outer, null, normals(outer), fringe, color, clearOf(color));
		double[] innerNormals = normals(inner);

		for (int i = 0; i < innerNormals.length; i++) {
			innerNormals[i] = -innerNormals[i];
		}

		edge(mesh, inner, null, innerNormals, fringe, color, clearOf(color));
		submit(graphics, mesh, null);
	}

	private static void pixelBorder(GuiGraphicsExtractor graphics, Affine matrix, double width, double height, double[] radii, double bw, int color) {
		Mesh mesh = new Mesh();
		int rows = (int) Math.ceil(height);
		double[] innerRadii = inset(radii, bw);

		for (int row = 0; row < rows; row++) {
			double y0 = row;
			double y1 = Math.min(height, row + 1);
			double[] outerSpan = span(width, height, radii, row);
			double[] innerSpan = row < bw || row >= height - bw ? null : span(width - bw * 2, height - bw * 2, innerRadii, (int) (row - bw));

			if (innerSpan == null) {
				quad(mesh, matrix, outerSpan[0], y0, outerSpan[1], y1, color);
			} else {
				quad(mesh, matrix, outerSpan[0], y0, innerSpan[0] + bw, y1, color);
				quad(mesh, matrix, innerSpan[1] + bw, y0, outerSpan[1], y1, color);
			}
		}

		submit(graphics, mesh, null);
	}

	private static double[] span(double width, double height, double[] radii, int row) {
		int top = (int) Math.round(radii[0]);
		int topRight = (int) Math.round(radii[1]);
		int bottomRight = (int) Math.round(radii[2]);
		int bottom = (int) Math.round(radii[3]);
		double left = 0;
		double right = width;
		int fromBottom = (int) Math.ceil(height) - 1 - row;

		if (row < top) {
			left = Math.max(left, inset(top, row));
		}

		if (row < topRight) {
			right = Math.min(right, width - inset(topRight, row));
		}

		if (fromBottom < bottom) {
			left = Math.max(left, inset(bottom, fromBottom));
		}

		if (fromBottom < bottomRight) {
			right = Math.min(right, width - inset(bottomRight, fromBottom));
		}

		return new double[] {left, right};
	}

	private static void quad(Mesh mesh, Affine matrix, double x0, double y0, double x1, double y1, int color) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}

		mesh.vertex(matrix.x(x0, y0), matrix.y(x0, y0), 0, 0, color);
		mesh.vertex(matrix.x(x0, y1), matrix.y(x0, y1), 0, 0, color);
		mesh.vertex(matrix.x(x1, y1), matrix.y(x1, y1), 0, 0, color);
		mesh.vertex(matrix.x(x1, y0), matrix.y(x1, y0), 0, 0, color);
	}

	static void shadow(GuiGraphicsExtractor graphics, Affine matrix, double width, double height, double[] radii, int color, double blur, double offsetX, double offsetY, double spread, double opacity) {
		int shaded = withOpacity(color, opacity);

		if ((shaded >>> 24) == 0) {
			return;
		}

		double grow = spread - blur / 2;
		double w = Math.max(0.01, width + grow * 2);
		double h = Math.max(0.01, height + grow * 2);
		double[] fitted = fitRadii(width, height, radii);
		double[] grown = fitRadii(w, h, new double[] {Math.max(0, fitted[0] + grow), Math.max(0, fitted[1] + grow), Math.max(0, fitted[2] + grow), Math.max(0, fitted[3] + grow)});
		double x = offsetX + width / 2 - w / 2;
		double y = offsetY + height / 2 - h / 2;
		double scale = matrix.scaleFactor() / pixel();
		double[] screen = toScreen(matrix, outline(x, y, w, h, grown, null, scale));
		Mesh mesh = new Mesh();
		fill(mesh, screen, null, shaded, 0);
		double fade = Math.max(blur * matrix.scaleFactor(), pixel());
		double[] normals = normals(screen);

		if (blur > 0) {
			int middle = withOpacity(shaded, 0.35);
			edge(mesh, screen, null, normals, fade * 0.45, shaded, middle);
			double[] outer = new double[screen.length];

			for (int i = 0; i < screen.length; i++) {
				outer[i] = screen[i] + normals[i] * fade * 0.45;
			}

			edge(mesh, outer, null, normals(outer), fade * 0.55, middle, clearOf(shaded));
		} else {
			edge(mesh, screen, null, normals, pixel(), shaded, clearOf(shaded));
		}

		submit(graphics, mesh, null);
	}

	static void texturedRect(GuiGraphicsExtractor graphics, Affine matrix, double x, double y, double width, double height, double @Nullable [] radii,
			GuiTextures.Texture texture, double u0, double v0, double u1, double v1, int tint) {
		if (width <= 0 || height <= 0 || (tint >>> 24) == 0) {
			return;
		}

		boolean rounded = radii != null && (radii[0] > 0 || radii[1] > 0 || radii[2] > 0 || radii[3] > 0);
		Mesh mesh = new Mesh();

		if (!rounded) {
			mesh.vertex(matrix.x(x, y), matrix.y(x, y), u0, v0, tint);
			mesh.vertex(matrix.x(x, y + height), matrix.y(x, y + height), u0, v1, tint);
			mesh.vertex(matrix.x(x + width, y + height), matrix.y(x + width, y + height), u1, v1, tint);
			mesh.vertex(matrix.x(x + width, y), matrix.y(x + width, y), u1, v0, tint);
			submit(graphics, mesh, texture);
			return;
		}

		double scale = matrix.scaleFactor() / pixel();
		double[] local = outline(x, y, width, height, fitRadii(width, height, radii), null, scale);
		double[] uv = new double[local.length];

		for (int i = 0; i < local.length; i += 2) {
			uv[i] = u0 + (local[i] - x) / width * (u1 - u0);
			uv[i + 1] = v0 + (local[i + 1] - y) / height * (v1 - v0);
		}

		fill(mesh, toScreen(matrix, local), uv, tint, pixel());
		submit(graphics, mesh, texture);
	}

	static void rect(GuiGraphicsExtractor graphics, Affine matrix, double x, double y, double width, double height, double radius, int color) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
			return;
		}

		Mesh mesh = new Mesh();

		if (radius <= 0 && matrix.isAxisAligned()) {
			quad(mesh, matrix, x, y, x + width, y + height, color);
			submit(graphics, mesh, null);
			return;
		}

		double scale = matrix.scaleFactor() / pixel();
		double[] local = outline(x, y, width, height, fitRadii(width, height, new double[] {radius, radius, radius, radius}), null, scale);
		fill(mesh, toScreen(matrix, local), null, color, pixel());
		submit(graphics, mesh, null);
	}

	static void line(GuiGraphicsExtractor graphics, Affine matrix, double x1, double y1, double x2, double y2, double width, int color) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double length = Math.hypot(dx, dy);

		if (length < 1e-6 || width <= 0 || (color >>> 24) == 0) {
			return;
		}

		double nx = -dy / length * width / 2;
		double ny = dx / length * width / 2;
		double[] local = {x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny};
		Mesh mesh = new Mesh();
		fill(mesh, toScreen(matrix, local), null, color, pixel());
		submit(graphics, mesh, null);
	}

	private static double[] circleOutline(double cx, double cy, double radius, double screenScale) {
		int n = Math.clamp((int) Math.ceil(Math.sqrt(radius * screenScale) * 6), 12, 160);
		double[] points = new double[n * 2];

		for (int i = 0; i < n; i++) {
			double angle = i / (double) n * Math.PI * 2;
			points[i * 2] = cx + Math.cos(angle) * radius;
			points[i * 2 + 1] = cy + Math.sin(angle) * radius;
		}

		return points;
	}

	static void circle(GuiGraphicsExtractor graphics, Affine matrix, double cx, double cy, double radius, int color) {
		if (radius <= 0 || (color >>> 24) == 0) {
			return;
		}

		Mesh mesh = new Mesh();
		fill(mesh, toScreen(matrix, circleOutline(cx, cy, radius, matrix.scaleFactor() / pixel())), null, color, pixel());
		submit(graphics, mesh, null);
	}

	static void ring(GuiGraphicsExtractor graphics, Affine matrix, double cx, double cy, double radius, double width, int color) {
		if (radius <= 0 || width <= 0 || (color >>> 24) == 0) {
			return;
		}

		if (width >= radius) {
			circle(graphics, matrix, cx, cy, radius, color);
			return;
		}

		double scale = matrix.scaleFactor() / pixel();
		double[] outer = toScreen(matrix, circleOutline(cx, cy, radius, scale));
		int n = outer.length / 2;
		double[] innerLocal = new double[n * 2];

		for (int i = 0; i < n; i++) {
			double angle = i / (double) n * Math.PI * 2;
			innerLocal[i * 2] = cx + Math.cos(angle) * (radius - width);
			innerLocal[i * 2 + 1] = cy + Math.sin(angle) * (radius - width);
		}

		double[] inner = toScreen(matrix, innerLocal);
		Mesh mesh = new Mesh();

		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			mesh.vertex(outer[i * 2], outer[i * 2 + 1], 0, 0, color);
			mesh.vertex(outer[j * 2], outer[j * 2 + 1], 0, 0, color);
			mesh.vertex(inner[j * 2], inner[j * 2 + 1], 0, 0, color);
			mesh.vertex(inner[i * 2], inner[i * 2 + 1], 0, 0, color);
		}

		edge(mesh, outer, null, normals(outer), pixel(), color, clearOf(color));
		double[] innerNormals = normals(inner);

		for (int i = 0; i < innerNormals.length; i++) {
			innerNormals[i] = -innerNormals[i];
		}

		edge(mesh, inner, null, innerNormals, pixel(), color, clearOf(color));
		submit(graphics, mesh, null);
	}
}
