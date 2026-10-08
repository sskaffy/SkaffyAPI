package me.skaffy.protocol.session;

@FunctionalInterface
public interface PacketSink {
	void send(String channel, byte[] data);
}
