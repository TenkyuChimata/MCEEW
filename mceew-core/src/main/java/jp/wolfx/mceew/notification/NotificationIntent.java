package jp.wolfx.mceew.notification;

import jp.wolfx.mceew.format.PlaceholderRenderer;

/**
 * Immutable description of the configured notification channels for one event.
 */
public final class NotificationIntent {
    private final NotificationSource source;
    private final boolean consoleDelivery;
    private final ChatNotice chat;
    private final TitleNotice title;
    private final SoundNotice sound;

    NotificationIntent(
            NotificationSource source,
            boolean consoleDelivery,
            ChatNotice chat,
            TitleNotice title,
            SoundNotice sound
    ) {
        this.source = source;
        this.consoleDelivery = consoleDelivery;
        this.chat = chat;
        this.title = title;
        this.sound = sound;
    }

    public NotificationSource getSource() {
        return source;
    }

    public String getPermissionNode() {
        return source.getPermissionNode();
    }

    public boolean isConsoleDelivery() {
        return consoleDelivery;
    }

    public ChatNotice getChat() {
        return chat;
    }

    public TitleNotice getTitle() {
        return title;
    }

    public SoundNotice getSound() {
        return sound;
    }

    public static final class ChatNotice {
        private final String template;
        private final String[] replacements;

        ChatNotice(String template, String... replacements) {
            this.template = template;
            this.replacements = replacements.clone();
        }

        public String render() {
            return PlaceholderRenderer.render(template, replacements);
        }
    }

    public static final class TitleNotice {
        private final ChatNotice title;
        private final ChatNotice subtitle;
        private final int fadeInTicks;
        private final int stayTicks;
        private final int fadeOutTicks;

        TitleNotice(
                ChatNotice title,
                ChatNotice subtitle,
                int fadeInTicks,
                int stayTicks,
                int fadeOutTicks
        ) {
            this.title = title;
            this.subtitle = subtitle;
            this.fadeInTicks = fadeInTicks;
            this.stayTicks = stayTicks;
            this.fadeOutTicks = fadeOutTicks;
        }

        public String renderTitle() {
            return title.render();
        }

        public String renderSubtitle() {
            return subtitle.render();
        }

        public int getFadeInTicks() {
            return fadeInTicks;
        }

        public int getStayTicks() {
            return stayTicks;
        }

        public int getFadeOutTicks() {
            return fadeOutTicks;
        }
    }

    public static final class SoundNotice {
        private final String key;
        private final double volume;
        private final double pitch;

        SoundNotice(String key, double volume, double pitch) {
            this.key = key;
            this.volume = volume;
            this.pitch = pitch;
        }

        public String getKey() {
            return key;
        }

        public double getVolume() {
            return volume;
        }

        public double getPitch() {
            return pitch;
        }
    }
}
