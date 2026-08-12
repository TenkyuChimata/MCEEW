package jp.wolfx.mceew.velocity;

import java.util.Objects;

/** Immutable effective delivery-channel policy for one notification recipient. */
final class VelocityChannelPolicy {
    private final boolean chat;
    private final boolean title;
    private final boolean sound;

    VelocityChannelPolicy(boolean chat, boolean title, boolean sound) {
        this.chat = chat;
        this.title = title;
        this.sound = sound;
    }

    boolean chat() {
        return chat;
    }

    boolean title() {
        return title;
    }

    boolean sound() {
        return sound;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VelocityChannelPolicy)) {
            return false;
        }
        VelocityChannelPolicy that = (VelocityChannelPolicy) other;
        return chat == that.chat && title == that.title && sound == that.sound;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chat, title, sound);
    }
}
