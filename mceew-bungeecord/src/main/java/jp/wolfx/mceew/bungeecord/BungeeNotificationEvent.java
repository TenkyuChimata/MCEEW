package jp.wolfx.mceew.bungeecord;

import jp.wolfx.mceew.notification.NotificationIntent;
import jp.wolfx.mceew.notification.NotificationSource;

/** Deferred core-intent construction for one processed Bungee notification event. */
interface BungeeNotificationEvent {
    NotificationSource source();

    NotificationIntent build(BungeeChannelPolicy channels);
}
