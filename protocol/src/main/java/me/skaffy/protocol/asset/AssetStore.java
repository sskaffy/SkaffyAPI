package me.skaffy.protocol.asset;

import java.io.IOException;
import java.util.List;

public interface AssetStore {
	long size(String id);

	AssetHash hash(String id) throws IOException;

	List<String> ids();

	long totalSize();

	int fileCount();

	void write(String id, byte[] data) throws IOException;

	void delete(String id) throws IOException;
}
