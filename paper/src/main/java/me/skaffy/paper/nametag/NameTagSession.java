package me.skaffy.paper.nametag;

import java.util.List;

public interface NameTagSession {
	void setSprites(List<String> assets);

	int spriteNumber(String asset);

	void sendNameTagDefinition(byte[] data);
}
