package me.skaffy.protocol.asset;

public record ServerAsset(byte[] data, AssetHash hash) {
	public static ServerAsset of(byte[] data) {
		return new ServerAsset(data, AssetHash.of(data));
	}
}
