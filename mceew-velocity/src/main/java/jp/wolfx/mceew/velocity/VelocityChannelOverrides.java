package jp.wolfx.mceew.velocity;

/** Nullable configuration values that override an inherited channel policy. */
final class VelocityChannelOverrides {
    static final VelocityChannelOverrides EMPTY = new VelocityChannelOverrides(null, null, null);

    private final Boolean chat;
    private final Boolean title;
    private final Boolean sound;

    VelocityChannelOverrides(Boolean chat, Boolean title, Boolean sound) {
        this.chat = chat;
        this.title = title;
        this.sound = sound;
    }

    VelocityChannelPolicy applyTo(VelocityChannelPolicy inherited) {
        return new VelocityChannelPolicy(
                chat == null ? inherited.chat() : chat,
                title == null ? inherited.title() : title,
                sound == null ? inherited.sound() : sound);
    }
}
