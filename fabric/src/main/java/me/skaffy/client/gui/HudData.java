package me.skaffy.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import me.skaffy.client.gui.sfy.SfyLibrary;
import me.skaffy.client.gui.text.RichText;
import me.skaffy.client.mixin.BossHealthOverlayAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

final class HudData {
	private static final Comparator<PlayerScoreEntry> SIDEBAR_ORDER = Comparator.comparing(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

	record BossBar(RichText name, double progress, String color, String style) {
	}

	record SidebarLine(RichText name, RichText score) {
	}

	record Sidebar(RichText title, List<Object> lines) {
	}

	record Effect(String id, String icon, int level, int duration, boolean ambient) {
	}

	private HudData() {
	}

	private static LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	static void register(SfyLibrary lib) {
		lib.type("BossBar", null, BossBar.class);
		lib.method("BossBar", "name", "RichText", "", (self, a) -> ((BossBar) self).name());
		lib.method("BossBar", "progress", "double", "", (self, a) -> ((BossBar) self).progress());
		lib.method("BossBar", "color", "String", "", (self, a) -> ((BossBar) self).color());
		lib.method("BossBar", "style", "String", "", (self, a) -> ((BossBar) self).style());
		lib.type("SidebarLine", null, SidebarLine.class);
		lib.method("SidebarLine", "name", "RichText", "", (self, a) -> ((SidebarLine) self).name());
		lib.method("SidebarLine", "score", "RichText", "", (self, a) -> ((SidebarLine) self).score());
		lib.type("Sidebar", null, Sidebar.class);
		lib.method("Sidebar", "title", "RichText", "", (self, a) -> ((Sidebar) self).title());
		lib.method("Sidebar", "lines", "List<SidebarLine>", "", (self, a) -> new ArrayList<>(((Sidebar) self).lines()));
		lib.type("Effect", null, Effect.class);
		lib.method("Effect", "id", "String", "", (self, a) -> ((Effect) self).id());
		lib.method("Effect", "icon", "String", "", (self, a) -> ((Effect) self).icon());
		lib.method("Effect", "level", "int", "", (self, a) -> ((Effect) self).level());
		lib.method("Effect", "duration", "int", "", (self, a) -> ((Effect) self).duration());
		lib.method("Effect", "ambient", "boolean", "", (self, a) -> ((Effect) self).ambient());

		lib.function("health", "double", "", (self, a) -> player() == null ? 0.0 : (double) player().getHealth());
		lib.function("maxHealth", "double", "", (self, a) -> player() == null ? 0.0 : (double) player().getMaxHealth());
		lib.function("absorption", "double", "", (self, a) -> player() == null ? 0.0 : (double) player().getAbsorptionAmount());
		lib.function("armor", "int", "", (self, a) -> player() == null ? 0 : player().getArmorValue());
		lib.function("food", "int", "", (self, a) -> player() == null ? 0 : player().getFoodData().getFoodLevel());
		lib.function("saturation", "double", "", (self, a) -> player() == null ? 0.0 : (double) player().getFoodData().getSaturationLevel());
		lib.function("air", "int", "", (self, a) -> player() == null ? 0 : player().getAirSupply());
		lib.function("maxAir", "int", "", (self, a) -> player() == null ? 0 : player().getMaxAirSupply());
		lib.function("xpLevel", "int", "", (self, a) -> player() == null ? 0 : player().experienceLevel);
		lib.function("xpProgress", "double", "", (self, a) -> player() == null ? 0.0 : (double) player().experienceProgress);
		lib.function("attackStrength", "double", "", (self, a) -> player() == null ? 1.0 : (double) player().getAttackStrengthScale(0));
		lib.function("selectedSlot", "int", "", (self, a) -> player() == null ? 0 : player().getInventory().getSelectedSlot());
		lib.function("bossBars", "List<BossBar>", "", (self, a) -> bossBars());
		lib.function("sidebar", "Sidebar", "", (self, a) -> sidebar());
		lib.function("effects", "List<Effect>", "", (self, a) -> effects());
	}

	static RichText rich(Component component) {
		List<RichText.Span> spans = new ArrayList<>();
		component.visit((style, text) -> {
			if (!text.isEmpty()) {
				spans.add(new RichText.Chars(text, new RichText.SpanStyle(style.getColor() == null ? null : style.getColor().getValue() & 0xFFFFFF,
						style.isBold() ? Boolean.TRUE : null, style.isItalic() ? Boolean.TRUE : null, style.isUnderlined() ? Boolean.TRUE : null,
						style.isStrikethrough() ? Boolean.TRUE : null, style.isObfuscated() ? Boolean.TRUE : null, null, null, null)));
			}

			return Optional.empty();
		}, Style.EMPTY);
		return new RichText(spans);
	}

	private static List<Object> bossBars() {
		List<Object> bars = new ArrayList<>();

		for (LerpingBossEvent event : ((BossHealthOverlayAccessor) Minecraft.getInstance().gui.hud.getBossOverlay()).skaffy$events().values()) {
			bars.add(new BossBar(rich(event.getName()), event.getProgress(), event.getColor().getSerializedName(), event.getOverlay().getSerializedName()));
		}

		return bars;
	}

	private static Sidebar sidebar() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level == null || minecraft.player == null) {
			return null;
		}

		Scoreboard scoreboard = minecraft.level.getScoreboard();
		Objective objective = null;
		PlayerTeam team = scoreboard.getPlayersTeam(minecraft.player.getScoreboardName());

		if (team != null) {
			Optional<TeamColor> color = team.getColor();

			if (color.isPresent()) {
				objective = scoreboard.getDisplayObjective(color.get().displaySlot());
			}
		}

		if (objective == null) {
			objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		}

		if (objective == null) {
			return null;
		}

		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<Object> lines = new ArrayList<>();

		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective).stream().filter(entry -> !entry.isHidden()).sorted(SIDEBAR_ORDER).limit(15).toList()) {
			Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
			lines.add(new SidebarLine(rich(name), rich(entry.formatValue(format))));
		}

		return new Sidebar(rich(objective.getDisplayName()), lines);
	}

	private static List<Object> effects() {
		List<Object> effects = new ArrayList<>();

		if (player() == null) {
			return effects;
		}

		for (MobEffectInstance instance : player().getActiveEffects()) {
			Holder<MobEffect> effect = instance.getEffect();
			String id = effect.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("minecraft:unknown");
			String icon = effect.unwrapKey().map(key -> key.identifier().withPrefix("mob_effect/").toString()).orElse("minecraft:mob_effect/speed");
			effects.add(new Effect(id, icon.toLowerCase(Locale.ROOT), instance.getAmplifier() + 1, instance.isInfiniteDuration() ? -1 : instance.getDuration(), instance.isAmbient()));
		}

		return effects;
	}
}
