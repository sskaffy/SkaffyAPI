package me.skaffy.client.gui.element;

import me.skaffy.client.gui.element.GuiEnums.ImageMode;

public final class PictureElements {
	private PictureElements() {
	}

	abstract static class Picture extends Element {
		public Object resource;

		Picture(GuiDocument document) {
			super(document);
		}

		@Override
		double[] intrinsicSize(double innerWidth, double innerHeight) {
			double[] size = document.platform.naturalSize(this);
			return size == null ? new double[] {16, 16} : size;
		}
	}

	public static final class ImageElement extends Picture {
		public String source = "";
		public boolean guiSprite;
		public ImageMode mode = ImageMode.STRETCH;
		public int[] slice = {4, 4, 4, 4};
		public int[] region;

		public ImageElement(GuiDocument document) {
			super(document);
		}

		@Override
		public String typeName() {
			return "Image";
		}
	}

	public static final class HeadElement extends Picture {
		public String player = "";
		public boolean hat = true;

		public HeadElement(GuiDocument document) {
			super(document);
		}

		@Override
		public String typeName() {
			return "Head";
		}
	}

	public static final class ItemElement extends Picture {
		public String item = "minecraft:air";
		public int slot = -1;
		public int count = 1;
		public boolean decorations = true;
		public boolean itemTooltip = true;

		public ItemElement(GuiDocument document) {
			super(document);
		}

		@Override
		public boolean catchesMouse() {
			return itemTooltip && mouseThrough == null || super.catchesMouse();
		}

		@Override
		public String typeName() {
			return "Item";
		}
	}

	public static final class EntityElement extends Picture {
		public String type;
		public boolean followMouse = true;
		public double yaw;
		public double pitch;

		public EntityElement(GuiDocument document) {
			super(document);
		}

		@Override
		public String typeName() {
			return "EntityView";
		}
	}
}
