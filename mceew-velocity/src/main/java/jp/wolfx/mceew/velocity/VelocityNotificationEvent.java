package jp.wolfx.mceew.velocity;

import jp.wolfx.mceew.notification.NotificationIntent;
import jp.wolfx.mceew.notification.NotificationSource;

/** Deferred core-intent construction for one processed Wolfx event. */
interface VelocityNotificationEvent {
    enum DeliveryStyle {
        JMA,
        REGIONAL,
        EARTHQUAKE_LIST
    }

    NotificationSource source();

    DeliveryStyle deliveryStyle();

    NotificationIntent build(VelocityChannelPolicy channels);
}
