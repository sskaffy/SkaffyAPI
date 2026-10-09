package me.skaffy.protocol.perspective;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.perspective.PerspectivePacket.Camera;
import me.skaffy.protocol.perspective.PerspectivePacket.Look;
import me.skaffy.protocol.perspective.PerspectivePacket.Perspective;
import me.skaffy.protocol.perspective.PerspectivePacket.PerspectiveChanged;
import me.skaffy.protocol.perspective.PerspectivePacket.PerspectiveInfo;
import me.skaffy.protocol.perspective.PerspectivePacket.QueryPerspective;
import me.skaffy.protocol.perspective.PerspectivePacket.SetAllowed;
import me.skaffy.protocol.perspective.PerspectivePacket.SetCamera;
import me.skaffy.protocol.perspective.PerspectivePacket.SetPerspective;

public final class PerspectiveCodec {
	public static final String FEATURE = "perspective";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int SET_PERSPECTIVE = 0;
	public static final int SET_CAMERA = 1;
	public static final int SET_ALLOWED = 2;
	public static final int QUERY_PERSPECTIVE = 3;

	public static final int PERSPECTIVE_INFO = 0;
	public static final int PERSPECTIVE_CHANGED = 1;

	private static final int LOOK_ANGLES = 0;
	private static final int LOOK_POINT = 1;
	private static final int LOOK_PLAYER = 2;
	private static final int LOOK_ENTITY = 3;
	private static final int LOOK_PLAYER_VIEW = 4;
	private static final int LOOK_ANCHOR_FACING = 5;

	private static final int ANCHOR_PLAYER = 0;
	private static final int ANCHOR_BONE = 1;

	private PerspectiveCodec() {
	}

	public static byte[] encode(PerspectivePacket packet) {
		PacketWriter writer = new PacketWriter(48);

		switch (packet) {
			case SetPerspective set -> writer.writeVarInt(SET_PERSPECTIVE)
					.writeByte(set.perspective().ordinal())
					.writeVarInt(set.durationMillis())
					.writeByte(set.easing().ordinal());
			case SetCamera set -> {
				writer.writeVarInt(SET_CAMERA);
				writeCamera(writer, set.camera());
				writer.writeVarInt(set.durationMillis()).writeByte(set.easing().ordinal());
			}
			case SetAllowed allowed -> writer.writeVarInt(SET_ALLOWED).writeByte(allowed.mask());
			case QueryPerspective query -> writer.writeVarInt(QUERY_PERSPECTIVE).writeVarInt(query.requestId());
			case PerspectiveInfo info -> writer.writeVarInt(PERSPECTIVE_INFO).writeVarInt(info.requestId()).writeByte(info.perspective().ordinal());
			case PerspectiveChanged changed -> writer.writeVarInt(PERSPECTIVE_CHANGED).writeByte(changed.from().ordinal()).writeByte(changed.to().ordinal());
		}

		return writer.toByteArray();
	}

	public static PerspectivePacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		PerspectivePacket packet = switch (id) {
			case SET_PERSPECTIVE -> new SetPerspective(perspective(reader), reader.readVarInt(), easing(reader));
			case SET_CAMERA -> new SetCamera(readCamera(reader), reader.readVarInt(), easing(reader));
			case SET_ALLOWED -> new SetAllowed(reader.readUnsignedByte());
			case QUERY_PERSPECTIVE -> new QueryPerspective(reader.readVarInt());
			default -> throw new ProtocolException("Unknown clientbound perspective packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static PerspectivePacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		PerspectivePacket packet = switch (id) {
			case PERSPECTIVE_INFO -> new PerspectiveInfo(reader.readVarInt(), perspective(reader));
			case PERSPECTIVE_CHANGED -> new PerspectiveChanged(perspective(reader), perspective(reader));
			default -> throw new ProtocolException("Unknown serverbound perspective packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	private static void writeCamera(PacketWriter writer, Camera camera) {
		writer.writeFloat(camera.x()).writeFloat(camera.y()).writeFloat(camera.z()).writeByte(camera.turn().ordinal());

		switch (camera.look()) {
			case Look.Angles angles -> writer.writeByte(LOOK_ANGLES).writeFloat(angles.yaw()).writeFloat(angles.pitch());
			case Look.Point point -> writer.writeByte(LOOK_POINT).writeFloat(point.x()).writeFloat(point.y()).writeFloat(point.z());
			case Look.AtPlayer ignored -> writer.writeByte(LOOK_PLAYER);
			case Look.AtEntity entity -> writer.writeByte(LOOK_ENTITY).writeVarInt(entity.entityId());
			case Look.PlayerView ignored -> writer.writeByte(LOOK_PLAYER_VIEW);
			case Look.AnchorFacing ignored -> writer.writeByte(LOOK_ANCHOR_FACING);
		}

		writer.writeFloat(camera.roll()).writeBoolean(camera.pullInFrontOfWalls());

		switch (camera.anchor()) {
			case PerspectivePacket.Anchor.Player ignored -> writer.writeByte(ANCHOR_PLAYER);
			case PerspectivePacket.Anchor.Bone bone -> writer.writeByte(ANCHOR_BONE)
					.writeString(bone.entity(), PerspectivePacket.Anchor.MAX_ID)
					.writeString(bone.bone(), PerspectivePacket.Anchor.MAX_BONE);
		}
	}

	private static Camera readCamera(PacketReader reader) {
		float x = reader.readFloat();
		float y = reader.readFloat();
		float z = reader.readFloat();
		PerspectivePacket.Turn turn = reader.readEnum(PerspectivePacket.Turn.values(), "camera turn");
		int kind = reader.readUnsignedByte();

		Look look = switch (kind) {
			case LOOK_ANGLES -> new Look.Angles(reader.readFloat(), reader.readFloat());
			case LOOK_POINT -> new Look.Point(reader.readFloat(), reader.readFloat(), reader.readFloat());
			case LOOK_PLAYER -> new Look.AtPlayer();
			case LOOK_ENTITY -> new Look.AtEntity(reader.readVarInt());
			case LOOK_PLAYER_VIEW -> new Look.PlayerView();
			case LOOK_ANCHOR_FACING -> new Look.AnchorFacing();
			default -> throw new ProtocolException("Unknown camera look " + kind);
		};

		float roll = reader.readFloat();
		boolean pull = reader.readBoolean();
		int anchorKind = reader.readUnsignedByte();
		PerspectivePacket.Anchor anchor = switch (anchorKind) {
			case ANCHOR_PLAYER -> new PerspectivePacket.Anchor.Player();
			case ANCHOR_BONE -> new PerspectivePacket.Anchor.Bone(reader.readString(PerspectivePacket.Anchor.MAX_ID), reader.readString(PerspectivePacket.Anchor.MAX_BONE));
			default -> throw new ProtocolException("Unknown camera anchor " + anchorKind);
		};

		return new Camera(x, y, z, turn, look, roll, pull, anchor);
	}

	private static Perspective perspective(PacketReader reader) {
		return reader.readEnum(Perspective.values(), "perspective");
	}

	private static Easing easing(PacketReader reader) {
		return reader.readEnum(Easing.values(), "easing");
	}
}
