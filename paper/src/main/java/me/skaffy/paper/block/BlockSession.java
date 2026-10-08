package me.skaffy.paper.block;

import java.util.List;

import me.skaffy.api.block.CustomBlockType;

public interface BlockSession {
	void setBlockTypes(List<CustomBlockType> types);

	int blockNumber(CustomBlockType type);

	CustomBlockType blockType(int number);

	void sendDefinition(byte[] data);
}
