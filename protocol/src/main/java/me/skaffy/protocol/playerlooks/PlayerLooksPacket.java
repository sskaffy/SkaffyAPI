package me.skaffy.protocol.playerlooks;

public sealed interface PlayerLooksPacket {
	record SetLook(int entity, PlayerLook look) implements PlayerLooksPacket {
	}

	record RemoveLook(int entity) implements PlayerLooksPacket {
	}
}
