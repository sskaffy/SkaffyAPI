package me.skaffy.api.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

public final class NameTag {
	public static final int MAX_LINES = 5;
	public static final float MAX_SCALE = 64;
	private static final NameTag EMPTY = builder().build();

	public enum RenderMode {
		DEFAULT,
		BLOCK,
		ALWAYS
	}

	public enum SneakMode {
		DEFAULT,
		HIDE,
		SOFT_HIDE,
		SHOW
	}

	public enum Alignment {
		CENTER,
		LEFT,
		RIGHT
	}

	private final List<NameTagLine> lines;
	private final RenderMode renderMode;
	private final SneakMode sneakMode;
	private final Alignment alignment;
	private final NameTagBackground background;
	private final float scale;
	private final float offsetX;
	private final float offsetY;
	private final float offsetZ;
	private final float maxDistance;
	private final int lineGap;
	private final boolean fullBright;
	private final boolean showWhenInvisible;
	private final boolean showToSelf;

	private NameTag(Builder builder) {
		this.lines = List.copyOf(builder.lines);
		this.renderMode = builder.renderMode;
		this.sneakMode = builder.sneakMode;
		this.alignment = builder.alignment;
		this.background = builder.background;
		this.scale = builder.scale;
		this.offsetX = builder.offsetX;
		this.offsetY = builder.offsetY;
		this.offsetZ = builder.offsetZ;
		this.maxDistance = builder.maxDistance;
		this.lineGap = builder.lineGap;
		this.fullBright = builder.fullBright;
		this.showWhenInvisible = builder.showWhenInvisible;
		this.showToSelf = builder.showToSelf;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static NameTag of(NameTagLine... lines) {
		return builder().lines(List.of(lines)).build();
	}

	public static NameTag of(Component component) {
		return builder().lines(component).build();
	}

	public static NameTag empty() {
		return EMPTY;
	}

	public Builder toBuilder() {
		return new Builder(this);
	}

	public NameTag withLine(int index, NameTagLine line) {
		if (index < 0 || index >= lines.size()) {
			throw new IndexOutOfBoundsException("The name tag has " + lines.size() + " lines, there's no line " + index);
		}

		List<NameTagLine> changed = new ArrayList<>(lines);
		changed.set(index, Objects.requireNonNull(line, "line"));
		return toBuilder().lines(changed).build();
	}

	public List<NameTagLine> getLines() {
		return lines;
	}

	public RenderMode getRenderMode() {
		return renderMode;
	}

	public SneakMode getSneakMode() {
		return sneakMode;
	}

	public Alignment getAlignment() {
		return alignment;
	}

	public NameTagBackground getBackground() {
		return background;
	}

	public float getScale() {
		return scale;
	}

	public float getOffsetX() {
		return offsetX;
	}

	public float getOffsetY() {
		return offsetY;
	}

	public float getOffsetZ() {
		return offsetZ;
	}

	public float getMaxDistance() {
		return maxDistance;
	}

	public int getLineGap() {
		return lineGap;
	}

	public boolean isFullBright() {
		return fullBright;
	}

	public boolean isShownWhenInvisible() {
		return showWhenInvisible;
	}

	public boolean isShownToSelf() {
		return showToSelf;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NameTag that && lines.equals(that.lines) && renderMode == that.renderMode && sneakMode == that.sneakMode
				&& alignment == that.alignment && background.equals(that.background) && scale == that.scale && offsetX == that.offsetX
				&& offsetY == that.offsetY && offsetZ == that.offsetZ && maxDistance == that.maxDistance && lineGap == that.lineGap
				&& fullBright == that.fullBright && showWhenInvisible == that.showWhenInvisible && showToSelf == that.showToSelf;
	}

	@Override
	public int hashCode() {
		return Objects.hash(lines, renderMode, sneakMode, alignment, background, scale, offsetX, offsetY, offsetZ, maxDistance, lineGap, fullBright, showWhenInvisible, showToSelf);
	}

	@Override
	public String toString() {
		return "NameTag" + lines;
	}

	public static final class Builder {
		private final List<NameTagLine> lines = new ArrayList<>();
		private RenderMode renderMode = RenderMode.DEFAULT;
		private SneakMode sneakMode = SneakMode.DEFAULT;
		private Alignment alignment = Alignment.CENTER;
		private NameTagBackground background = NameTagBackground.vanilla();
		private float scale = 1;
		private float offsetX;
		private float offsetY;
		private float offsetZ;
		private float maxDistance;
		private int lineGap = 1;
		private boolean fullBright;
		private boolean showWhenInvisible;
		private boolean showToSelf;

		private Builder() {
		}

		private Builder(NameTag tag) {
			this.lines.addAll(tag.lines);
			this.renderMode = tag.renderMode;
			this.sneakMode = tag.sneakMode;
			this.alignment = tag.alignment;
			this.background = tag.background;
			this.scale = tag.scale;
			this.offsetX = tag.offsetX;
			this.offsetY = tag.offsetY;
			this.offsetZ = tag.offsetZ;
			this.maxDistance = tag.maxDistance;
			this.lineGap = tag.lineGap;
			this.fullBright = tag.fullBright;
			this.showWhenInvisible = tag.showWhenInvisible;
			this.showToSelf = tag.showToSelf;
		}

		public Builder line(NameTagLine line) {
			if (lines.size() >= MAX_LINES) {
				throw new IllegalArgumentException("A name tag has at most " + MAX_LINES + " lines");
			}

			lines.add(Objects.requireNonNull(line, "line"));
			return this;
		}

		public Builder line(NameTagObject... objects) {
			return line(NameTagLine.of(objects));
		}

		public Builder line(Component component) {
			return line(NameTagLine.of(component));
		}

		public Builder line(String text) {
			return line(NameTagLine.of(text));
		}

		public Builder lines(Component component) {
			ComponentConverter.lines(component).forEach(this::line);
			return this;
		}

		public Builder lines(List<NameTagLine> lines) {
			this.lines.clear();
			lines.forEach(this::line);
			return this;
		}

		public Builder renderMode(RenderMode renderMode) {
			this.renderMode = Objects.requireNonNull(renderMode, "renderMode");
			return this;
		}

		public Builder sneakMode(SneakMode sneakMode) {
			this.sneakMode = Objects.requireNonNull(sneakMode, "sneakMode");
			return this;
		}

		public Builder alignment(Alignment alignment) {
			this.alignment = Objects.requireNonNull(alignment, "alignment");
			return this;
		}

		public Builder background(NameTagBackground background) {
			this.background = Objects.requireNonNull(background, "background");
			return this;
		}

		public Builder scale(float scale) {
			if (!(scale > 0) || scale > MAX_SCALE) {
				throw new IllegalArgumentException("Scale must be above 0 and at most " + MAX_SCALE + ", got " + scale);
			}

			this.scale = scale;
			return this;
		}

		public Builder offset(double x, double y, double z) {
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				throw new IllegalArgumentException("Offset must be finite");
			}

			this.offsetX = (float) x;
			this.offsetY = (float) y;
			this.offsetZ = (float) z;
			return this;
		}

		public Builder maxDistance(double blocks) {
			if (!(blocks >= 0) || !Double.isFinite(blocks)) {
				throw new IllegalArgumentException("Distance must be 0 or more, got " + blocks);
			}

			this.maxDistance = (float) blocks;
			return this;
		}

		public Builder lineGap(int pixels) {
			if (pixels < 0 || pixels > NameTagBackground.MAX_PIXELS) {
				throw new IllegalArgumentException("Line gap must be 0 to " + NameTagBackground.MAX_PIXELS + ", got " + pixels);
			}

			this.lineGap = pixels;
			return this;
		}

		public Builder fullBright(boolean fullBright) {
			this.fullBright = fullBright;
			return this;
		}

		public Builder showWhenInvisible(boolean show) {
			this.showWhenInvisible = show;
			return this;
		}

		public Builder showToSelf(boolean show) {
			this.showToSelf = show;
			return this;
		}

		public NameTag build() {
			return new NameTag(this);
		}
	}
}
