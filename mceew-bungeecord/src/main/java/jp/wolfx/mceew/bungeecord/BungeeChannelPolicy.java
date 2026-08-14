package jp.wolfx.mceew.bungeecord;

/** Immutable effective Bungee delivery-channel policy for one notification recipient. */
final class BungeeChannelPolicy {
    private final boolean broadcast;
    private final boolean title;

    BungeeChannelPolicy(boolean broadcast, boolean title) {
        this.broadcast = broadcast;
        this.title = title;
    }

    boolean broadcast() {
        return broadcast;
    }

    boolean title() {
        return title;
    }
}
