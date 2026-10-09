package me.skaffy.paper.block;

import java.util.UUID;
import java.util.function.Function;

import me.skaffy.api.block.PlacedCustomBlock;
import me.skaffy.api.event.block.SkaffyBlockMinedEvent;
import me.skaffy.api.event.block.SkaffyBlockMiningAbortEvent;
import me.skaffy.api.event.block.SkaffyBlockMiningStartEvent;
import me.skaffy.api.event.block.SkaffyBlockPickEvent;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.blocks.BlocksPacket;
import me.skaffy.protocol.blocks.BlocksPacket.MiningAbort;
import me.skaffy.protocol.blocks.BlocksPacket.MiningFinish;
import me.skaffy.protocol.blocks.BlocksPacket.MiningStart;
import me.skaffy.protocol.blocks.BlocksPacket.PickBlock;
import me.skaffy.protocol.blocks.BlocksPacket.StopMining;
import me.skaffy.protocol.blocks.Positions;
import me.skaffy.paper.block.PaperCustomBlocks.Placement;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class BlockPacketListener implements PluginMessageListener {
	private final Plugin plugin;
	private final PaperCustomBlocks blocks;
	private final Function<UUID, BlockSession> sessions;

	public BlockPacketListener(Plugin plugin, PaperCustomBlocks blocks, Function<UUID, BlockSession> sessions) {
		this.plugin = plugin;
		this.blocks = blocks;
		this.sessions = sessions;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		BlockSession session = sessions.apply(player.getUniqueId());

		if (!BlocksCodec.CHANNEL.equals(channel) || session == null) {
			return;
		}

		BlocksPacket packet;

		try {
			packet = BlocksCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			return;
		}

		switch (packet) {
			case MiningStart start -> {
				PlacedCustomBlock block = find(player, session, start.blockPos(), start.block());

				if (block != null && !new SkaffyBlockMiningStartEvent(player, block).callEvent()) {
					player.sendPluginMessage(plugin, BlocksCodec.CHANNEL, BlocksCodec.encode(new StopMining(start.blockPos())));
				}
			}
			case MiningAbort abort -> {
				PlacedCustomBlock block = find(player, session, abort.blockPos(), -1);

				if (block != null) {
					new SkaffyBlockMiningAbortEvent(player, block).callEvent();
				}
			}
			case MiningFinish finish -> {
				PlacedCustomBlock block = find(player, session, finish.blockPos(), finish.block());

				if (block != null) {
					new SkaffyBlockMinedEvent(player, block).callEvent();
				}
			}
			case PickBlock pick -> {
				PlacedCustomBlock block = find(player, session, pick.blockPos(), pick.block());

				if (block != null) {
					new SkaffyBlockPickEvent(player, block).callEvent();
				}
			}
			default -> {
			}
		}
	}

	private PlacedCustomBlock find(Player player, BlockSession session, long pos, int number) {
		Location location = new Location(player.getWorld(), Positions.blockX(pos), Positions.blockY(pos), Positions.blockZ(pos));
		Placement placement = blocks.visibleTo(player.getUniqueId(), location);

		if (placement == null) {
			return null;
		}

		if (number >= 0 && (session.blockType(number) == null || !session.blockType(number).getName().equals(placement.type().getName()))) {
			return null;
		}

		return placement.placed(location);
	}
}
