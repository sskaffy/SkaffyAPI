package me.skaffy.client.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import com.mojang.blaze3d.platform.NativeImage;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class ImageDecoding {
	private ImageDecoding() {
	}

	public static NativeImage decode(byte[] bytes) throws IOException {
		if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
			return NativeImage.read(bytes);
		}

		ByteBuffer data = MemoryUtil.memAlloc(bytes.length);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			data.put(bytes).flip();
			IntBuffer width = stack.mallocInt(1);
			IntBuffer height = stack.mallocInt(1);
			IntBuffer components = stack.mallocInt(1);
			ByteBuffer pixels = STBImage.stbi_load_from_memory(data, width, height, components, 4);

			if (pixels == null) {
				throw new IOException("Could not read image: " + STBImage.stbi_failure_reason());
			}

			return new NativeImage(NativeImage.Format.RGBA, width.get(0), height.get(0), true, MemoryUtil.memAddress(pixels));
		} finally {
			MemoryUtil.memFree(data);
		}
	}
}
