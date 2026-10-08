package me.skaffy.api.event.gui;

import me.skaffy.api.gui.GuiMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class SkaffyGuiMessageEvent extends SkaffyGuiEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final GuiMessage message;

	public SkaffyGuiMessageEvent(Player player, GuiMessage message) {
		super(player, message.className());
		this.message = message;
	}

	public GuiMessage getMessage() {
		return message;
	}

	public String getName() {
		return message.name();
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
