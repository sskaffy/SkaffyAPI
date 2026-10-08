package me.skaffy.protocol;

import java.util.Set;
import java.util.regex.Pattern;

public final class Protocol {
	public static final int VERSION = 1;

	public static final String NAMESPACE = "skaffy";
	public static final String CORE_CHANNEL = NAMESPACE + ":core";

	public static final int MAX_CLIENTBOUND_PAYLOAD = 1_048_576;
	public static final int MAX_SERVERBOUND_PAYLOAD = 32_767;

	public static final int MAX_ASSET_SIZE = 16 * 1024 * 1024;
	public static final long MAX_CACHE_SIZE = 2048L * 1024 * 1024;
	public static final int MAX_CACHE_FILES = 8192;

	public static final int MAX_CHUNK_SIZE = 256 * 1024;
	public static final int MAX_QUERY_IDS = 256;
	public static final int MAX_DELETE_IDS = 1024;
	public static final int MAX_FEATURES = 256;

	public static final int MAX_ASSET_ID_LENGTH = 64;
	public static final int MAX_MOD_VERSION_LENGTH = 32;
	public static final int MAX_FAILURE_REASON_LENGTH = 256;

	private static final Pattern FEATURE_ID = Pattern.compile("[a-z0-9_]{1,32}");
	private static final Pattern ASSET_ID = Pattern.compile("[a-z0-9_-][a-z0-9_.-]*");
	private static final Set<String> WINDOWS_RESERVED = Set.of(
			"con", "prn", "aux", "nul",
			"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
			"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	private Protocol() {
	}

	public static String featureChannel(String featureId) {
		return NAMESPACE + ":" + featureId;
	}

	public static boolean isValidFeatureId(String id) {
		return id != null && !id.equals("core") && FEATURE_ID.matcher(id).matches();
	}

	public static boolean isValidAssetId(String id) {
		if (id == null || id.isEmpty() || id.length() > MAX_ASSET_ID_LENGTH || id.endsWith(".") || !ASSET_ID.matcher(id).matches()) {
			return false;
		}

		int dot = id.indexOf('.');
		return !WINDOWS_RESERVED.contains(dot < 0 ? id : id.substring(0, dot));
	}

	public static String requireAssetId(String id) {
		if (!isValidAssetId(id)) {
			throw new ProtocolException("Invalid asset id " + id);
		}

		return id;
	}
}
