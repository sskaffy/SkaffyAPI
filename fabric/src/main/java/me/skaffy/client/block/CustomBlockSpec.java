package me.skaffy.client.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.math.Quadrant;

import me.skaffy.protocol.blocks.BlockDefinition;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CustomBlockSpec {
	private final int number;
	private final BlockDefinition definition;
	private final VoxelShape[] collision = new VoxelShape[16];
	private final VoxelShape[] hitbox = new VoxelShape[16];
	private final VoxelShape[] occlusion = new VoxelShape[16];

	CustomBlockSpec(int number, BlockDefinition definition, BlockModels models) {
		this.number = number;
		this.definition = definition;

		VoxelShape collisionShape = definition.collision() == null ? Shapes.empty() : models.shape(definition.collision());
		VoxelShape hitboxShape = definition.hitbox() != null ? models.shape(definition.hitbox()) : collisionShape;
		VoxelShape occlusionShape = definition.transparency() == BlockDefinition.Transparency.SOLID ? models.shape(definition.model()) : Shapes.empty();

		for (int rotation = 0; rotation < 16; rotation++) {
			OctahedralGroup group = group(rotation);
			collision[rotation] = rotate(collisionShape, group);
			hitbox[rotation] = rotate(hitboxShape, group);
			occlusion[rotation] = rotate(occlusionShape, group);
		}
	}

	static OctahedralGroup group(int rotation) {
		Quadrant[] quadrants = Quadrant.values();
		return Quadrant.fromXYAngles(quadrants[rotation >> 2 & 3], quadrants[rotation & 3]);
	}

	private static VoxelShape rotate(VoxelShape shape, OctahedralGroup group) {
		return shape.isEmpty() || group == OctahedralGroup.IDENTITY ? shape : Shapes.rotate(shape, group);
	}

	public int number() {
		return number;
	}

	public BlockDefinition definition() {
		return definition;
	}

	VoxelShape collision(int rotation) {
		return collision[rotation];
	}

	VoxelShape hitbox(int rotation) {
		return hitbox[rotation];
	}

	VoxelShape occlusion(int rotation) {
		return occlusion[rotation];
	}

	float destroyProgress(Player player) {
		float hardness = definition.hardness();

		if (hardness < 0) {
			return 0;
		}

		BlockState toolBlock = switch (definition.tool()) {
			case PICKAXE -> Blocks.STONE.defaultBlockState();
			case AXE -> Blocks.OAK_PLANKS.defaultBlockState();
			case SHOVEL -> Blocks.DIRT.defaultBlockState();
			case HOE -> Blocks.HAY_BLOCK.defaultBlockState();
			case SWORD -> Blocks.MELON.defaultBlockState();
			case NONE -> Blocks.GLASS.defaultBlockState();
		};

		boolean correctTool = !definition.requiresTool() || player.getMainHandItem().isCorrectToolForDrops(toolBlock);
		return player.getDestroySpeed(toolBlock) / hardness / (correctTool ? 30 : 100);
	}
}
