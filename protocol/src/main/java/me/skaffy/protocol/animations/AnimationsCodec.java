package me.skaffy.protocol.animations;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.animations.AnimationsPacket.Attach;
import me.skaffy.protocol.animations.AnimationsPacket.Attachment;
import me.skaffy.protocol.animations.AnimationsPacket.Button;
import me.skaffy.protocol.animations.AnimationsPacket.Clear;
import me.skaffy.protocol.animations.AnimationsPacket.Click;
import me.skaffy.protocol.animations.AnimationsPacket.Display;
import me.skaffy.protocol.animations.AnimationsPacket.FollowPath;
import me.skaffy.protocol.animations.AnimationsPacket.LookAt;
import me.skaffy.protocol.animations.AnimationsPacket.Move;
import me.skaffy.protocol.animations.AnimationsPacket.PathPoint;
import me.skaffy.protocol.animations.AnimationsPacket.Placement;
import me.skaffy.protocol.animations.AnimationsPacket.Play;
import me.skaffy.protocol.animations.AnimationsPacket.Remove;
import me.skaffy.protocol.animations.AnimationsPacket.SetBone;
import me.skaffy.protocol.animations.AnimationsPacket.SetItem;
import me.skaffy.protocol.animations.AnimationsPacket.SetSettings;
import me.skaffy.protocol.animations.AnimationsPacket.SetVariables;
import me.skaffy.protocol.animations.AnimationsPacket.Settings;
import me.skaffy.protocol.animations.AnimationsPacket.Spawn;
import me.skaffy.protocol.animations.AnimationsPacket.Stop;
import me.skaffy.protocol.animations.AnimationsPacket.Target;
import me.skaffy.protocol.entitymodels.EntityModelsCodec;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.PlayAnimation.Mode;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class AnimationsCodec {
	public static final String FEATURE = "animations";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int SPAWN = 0;
	public static final int REMOVE = 1;
	public static final int CLEAR = 2;
	public static final int MOVE = 3;
	public static final int FOLLOW_PATH = 4;
	public static final int ATTACH = 5;
	public static final int PLAY = 6;
	public static final int STOP = 7;
	public static final int SET_VARIABLES = 8;
	public static final int SET_BONE = 9;
	public static final int LOOK_AT = 10;
	public static final int SET_ITEM = 11;
	public static final int SET_SETTINGS = 12;

	public static final int CLICK = 0;

	private AnimationsCodec() {
	}

	public static byte[] encode(AnimationsPacket packet) {
		PacketWriter writer = new PacketWriter(64);

		switch (packet) {
			case Spawn spawn -> {
				writer.writeVarInt(SPAWN).writeString(spawn.id(), AnimationsPacket.MAX_ID).writeVarInt(spawn.model());
				writePlacement(writer, spawn.placement());
				writeSettings(writer, spawn.settings());
			}
			case Remove remove -> writer.writeVarInt(REMOVE).writeString(remove.id(), AnimationsPacket.MAX_ID);
			case Clear clear -> writer.writeVarInt(CLEAR).writeString(clear.prefix(), AnimationsPacket.MAX_ID);
			case Move move -> {
				writer.writeVarInt(MOVE).writeString(move.id(), AnimationsPacket.MAX_ID);
				writePlacement(writer, move.placement());
				writer.writeVarInt(move.duration()).writeByte(move.easing().ordinal());
			}
			case FollowPath path -> {
				writer.writeVarInt(FOLLOW_PATH).writeString(path.id(), AnimationsPacket.MAX_ID);
				writer.writeList(path.points(), (w, point) -> w.writeVarInt(point.time()).writeDouble(point.x()).writeDouble(point.y()).writeDouble(point.z())
						.writeFloat(point.yaw()).writeFloat(point.pitch()).writeFloat(point.roll()));
				writer.writeBoolean(path.smooth()).writeBoolean(path.loop()).writeBoolean(path.faceAlong()).writeVarInt(path.start());
			}
			case Attach attach -> {
				writer.writeVarInt(ATTACH).writeString(attach.id(), AnimationsPacket.MAX_ID);

				switch (attach.attachment()) {
					case Attachment.None ignored -> writer.writeByte(0);
					case Attachment.ToEntity entity -> writer.writeByte(1).writeVarInt(entity.entity())
							.writeFloat(entity.x()).writeFloat(entity.y()).writeFloat(entity.z())
							.writeFloat(entity.yaw()).writeFloat(entity.pitch()).writeFloat(entity.roll()).writeBoolean(entity.turn());
					case Attachment.ToBone bone -> writer.writeByte(2).writeString(bone.parent(), AnimationsPacket.MAX_ID).writeString(bone.bone(), AnimationsPacket.MAX_BONE)
							.writeFloat(bone.x()).writeFloat(bone.y()).writeFloat(bone.z())
							.writeFloat(bone.yaw()).writeFloat(bone.pitch()).writeFloat(bone.roll());
				}
			}
			case Play play -> writer.writeVarInt(PLAY).writeString(play.id(), AnimationsPacket.MAX_ID).writeVarInt(play.animation()).writeByte(play.mode().ordinal())
					.writeFloat(play.speed()).writeFloat(play.start()).writeFloat(play.fadeIn()).writeBoolean(play.removeWhenDone());
			case Stop stop -> writer.writeVarInt(STOP).writeString(stop.id(), AnimationsPacket.MAX_ID).writeVarInt(stop.animation()).writeFloat(stop.fadeOut());
			case SetVariables set -> {
				writer.writeVarInt(SET_VARIABLES).writeString(set.id(), AnimationsPacket.MAX_ID);
				EntityModelsCodec.writeVariables(writer, set.variables());
			}
			case SetBone bone -> writer.writeVarInt(SET_BONE).writeString(bone.id(), AnimationsPacket.MAX_ID).writeString(bone.bone(), AnimationsPacket.MAX_BONE)
					.writeBoolean(bone.hidden()).writeInt(bone.tint())
					.writeFloat(bone.rotationX()).writeFloat(bone.rotationY()).writeFloat(bone.rotationZ())
					.writeFloat(bone.positionX()).writeFloat(bone.positionY()).writeFloat(bone.positionZ())
					.writeFloat(bone.scaleX()).writeFloat(bone.scaleY()).writeFloat(bone.scaleZ())
					.writeVarInt(bone.duration()).writeByte(bone.easing().ordinal());
			case LookAt look -> {
				writer.writeVarInt(LOOK_AT).writeString(look.id(), AnimationsPacket.MAX_ID).writeString(look.bone(), AnimationsPacket.MAX_BONE);

				switch (look.target()) {
					case Target.None ignored -> writer.writeByte(0);
					case Target.AtEntity entity -> writer.writeByte(1).writeVarInt(entity.entity());
					case Target.AtCamera ignored -> writer.writeByte(2);
					case Target.AtPoint point -> writer.writeByte(3).writeDouble(point.x()).writeDouble(point.y()).writeDouble(point.z());
				}

				writer.writeFloat(look.maxYaw()).writeFloat(look.maxPitch()).writeFloat(look.speed());
			}
			case SetItem item -> {
				writer.writeVarInt(SET_ITEM).writeString(item.id(), AnimationsPacket.MAX_ID).writeString(item.slot(), AnimationsPacket.MAX_SLOT)
						.writeString(item.bone(), AnimationsPacket.MAX_BONE);

				switch (item.display()) {
					case Display.None ignored -> writer.writeByte(0);
					case Display.Item stack -> writer.writeByte(1).writeString(stack.item(), AnimationsPacket.MAX_ITEM).writeString(stack.context(), AnimationsPacket.MAX_DISPLAY);
					case Display.Block block -> writer.writeByte(2).writeString(block.state(), AnimationsPacket.MAX_BLOCK);
				}

				writer.writeFloat(item.x()).writeFloat(item.y()).writeFloat(item.z())
						.writeFloat(item.rotationX()).writeFloat(item.rotationY()).writeFloat(item.rotationZ()).writeFloat(item.scale());
			}
			case SetSettings set -> {
				writer.writeVarInt(SET_SETTINGS).writeString(set.id(), AnimationsPacket.MAX_ID);
				writeSettings(writer, set.settings());
			}
			case Click click -> writer.writeVarInt(CLICK).writeString(click.id(), AnimationsPacket.MAX_ID).writeByte(click.button().ordinal()).writeBoolean(click.offHand())
					.writeFloat(click.x()).writeFloat(click.y()).writeFloat(click.z()).writeBoolean(click.sneaking());
		}

		if (writer.size() > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + Protocol.MAX_CLIENTBOUND_PAYLOAD);
		}

		return writer.toByteArray();
	}

	public static AnimationsPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		AnimationsPacket packet = switch (id) {
			case SPAWN -> new Spawn(id(reader), reader.readVarInt(), readPlacement(reader), readSettings(reader));
			case REMOVE -> new Remove(id(reader));
			case CLEAR -> new Clear(reader.readString(AnimationsPacket.MAX_ID));
			case MOVE -> new Move(id(reader), readPlacement(reader), reader.readVarInt(), easing(reader));
			case FOLLOW_PATH -> new FollowPath(id(reader),
					reader.readList(AnimationsPacket.MAX_PATH_POINTS, r -> new PathPoint(r.readVarInt(), r.readDouble(), r.readDouble(), r.readDouble(), r.readFloat(), r.readFloat(), r.readFloat())),
					reader.readBoolean(), reader.readBoolean(), reader.readBoolean(), reader.readVarInt());
			case ATTACH -> {
				String entityId = id(reader);
				int kind = reader.readUnsignedByte();
				Attachment attachment = switch (kind) {
					case 0 -> new Attachment.None();
					case 1 -> new Attachment.ToEntity(reader.readVarInt(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(),
							reader.readBoolean());
					case 2 -> new Attachment.ToBone(reader.readString(AnimationsPacket.MAX_ID), reader.readString(AnimationsPacket.MAX_BONE), reader.readFloat(), reader.readFloat(), reader.readFloat(),
							reader.readFloat(), reader.readFloat(), reader.readFloat());
					default -> throw new ProtocolException("Unknown attachment " + kind);
				};
				yield new Attach(entityId, attachment);
			}
			case PLAY -> new Play(id(reader), reader.readVarInt(), reader.readEnum(Mode.values(), "animation mode"), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readBoolean());
			case STOP -> new Stop(id(reader), reader.readVarInt(), reader.readFloat());
			case SET_VARIABLES -> new SetVariables(id(reader), EntityModelsCodec.readVariables(reader));
			case SET_BONE -> new SetBone(id(reader), reader.readString(AnimationsPacket.MAX_BONE), reader.readBoolean(), reader.readInt(),
					reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(),
					reader.readVarInt(), easing(reader));
			case LOOK_AT -> {
				String entityId = id(reader);
				String bone = reader.readString(AnimationsPacket.MAX_BONE);
				int kind = reader.readUnsignedByte();
				Target target = switch (kind) {
					case 0 -> new Target.None();
					case 1 -> new Target.AtEntity(reader.readVarInt());
					case 2 -> new Target.AtCamera();
					case 3 -> new Target.AtPoint(reader.readDouble(), reader.readDouble(), reader.readDouble());
					default -> throw new ProtocolException("Unknown look target " + kind);
				};
				yield new LookAt(entityId, bone, target, reader.readFloat(), reader.readFloat(), reader.readFloat());
			}
			case SET_ITEM -> {
				String entityId = id(reader);
				String slot = reader.readString(AnimationsPacket.MAX_SLOT);
				String bone = reader.readString(AnimationsPacket.MAX_BONE);
				int kind = reader.readUnsignedByte();
				Display display = switch (kind) {
					case 0 -> new Display.None();
					case 1 -> new Display.Item(reader.readString(AnimationsPacket.MAX_ITEM), reader.readString(AnimationsPacket.MAX_DISPLAY));
					case 2 -> new Display.Block(reader.readString(AnimationsPacket.MAX_BLOCK));
					default -> throw new ProtocolException("Unknown display " + kind);
				};
				yield new SetItem(entityId, slot, bone, display, reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(),
						reader.readFloat());
			}
			case SET_SETTINGS -> new SetSettings(id(reader), readSettings(reader));
			default -> throw new ProtocolException("Unknown clientbound animations packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static AnimationsPacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		AnimationsPacket packet = switch (id) {
			case CLICK -> new Click(id(reader), reader.readEnum(Button.values(), "click button"), reader.readBoolean(), reader.readFloat(), reader.readFloat(), reader.readFloat(),
					reader.readBoolean());
			default -> throw new ProtocolException("Unknown serverbound animations packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	private static String id(PacketReader reader) {
		return reader.readString(AnimationsPacket.MAX_ID);
	}

	private static Easing easing(PacketReader reader) {
		return reader.readEnum(Easing.values(), "easing");
	}

	private static void writePlacement(PacketWriter writer, Placement placement) {
		writer.writeString(placement.dimension(), AnimationsPacket.MAX_DIMENSION).writeDouble(placement.x()).writeDouble(placement.y()).writeDouble(placement.z())
				.writeFloat(placement.yaw()).writeFloat(placement.pitch()).writeFloat(placement.roll())
				.writeFloat(placement.scaleX()).writeFloat(placement.scaleY()).writeFloat(placement.scaleZ());
	}

	private static Placement readPlacement(PacketReader reader) {
		return new Placement(reader.readString(AnimationsPacket.MAX_DIMENSION), reader.readDouble(), reader.readDouble(), reader.readDouble(),
				reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat());
	}

	private static void writeSettings(PacketWriter writer, Settings settings) {
		writer.writeFloat(settings.hitboxWidth()).writeFloat(settings.hitboxHeight()).writeBoolean(settings.fullBright()).writeInt(settings.glowColor())
				.writeFloat(settings.shadowRadius()).writeFloat(settings.viewDistance()).writeBoolean(settings.showInFirstPerson());
	}

	private static Settings readSettings(PacketReader reader) {
		return new Settings(reader.readFloat(), reader.readFloat(), reader.readBoolean(), reader.readInt(), reader.readFloat(), reader.readFloat(), reader.readBoolean());
	}
}
