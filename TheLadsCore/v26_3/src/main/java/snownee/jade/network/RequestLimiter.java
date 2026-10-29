package snownee.jade.network;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * Lads: how fast one player may make the server run Jade's data providers (block and entity requests). A normal client asks about
 * four times a second (ObjectDataCenter.rateLimiter, 250 ms); this allows ten times that, with a burst, and drops the rest, so a
 * guest of a LAN or friends world cannot flood the host's server thread. The 26.3.4 release of Jade added a limit of its own.
 */
public final class RequestLimiter {
	static final int PER_SECOND = 40;
	static final int BURST = 40;
	/** Weak keys: a player's bucket goes away with the player. */
	private static final Map<ServerPlayer, Bucket> BUCKETS = Collections.synchronizedMap(new WeakHashMap<>());

	private RequestLimiter() {
	}

	/** False when this player is over the limit: drop the request. */
	public static boolean allow(ServerPlayer player) {
		return BUCKETS.computeIfAbsent(player, key -> new Bucket(System.nanoTime())).take(System.nanoTime());
	}

	static final class Bucket {
		private double tokens = BURST;
		private long last;

		Bucket(long nowNanos) {
			last = nowNanos;
		}

		synchronized boolean take(long nowNanos) {
			tokens = Math.min(BURST, tokens + (nowNanos - last) * PER_SECOND / 1.0e9);
			last = nowNanos;
			if (tokens < 1) {
				return false;
			}
			tokens -= 1;
			return true;
		}
	}
}
