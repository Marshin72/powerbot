package org.powerbot.bot.rt4.activation;

import java.awt.Graphics;
import java.util.Arrays;

import org.powerbot.script.TextPaintListener;
import org.powerbot.script.rt4.ClientContext;
import org.powerbot.script.rt4.Player;

/**
 */
public class TPlayer implements TextPaintListener {
	private final ClientContext ctx;

	public TPlayer(final ClientContext ctx) {
		this.ctx = ctx;
	}

	public int draw(int idx, final Graphics render) {
		final Player player = ctx.players.local();
		DebugHelper.drawLine(render, idx++, String.format("[%s] A: %d, CBL: %d, HP: %d, T: %d, S: %d, INT: %s", player.name(), player.animation(), player.combatLevel(), player.health(), player.team(), player.speed(), player.interacting()));
		DebugHelper.drawLine(render, idx++, String.format("COMBAT: %s, APP (VE): %s", Boolean.toString(player.inCombat()), Arrays.toString(player.appearance())));
		return idx;
	}
}
