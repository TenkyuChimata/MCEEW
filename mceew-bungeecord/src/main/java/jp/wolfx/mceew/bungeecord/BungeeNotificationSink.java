package jp.wolfx.mceew.bungeecord;

import jp.wolfx.mceew.BungeeMessageProcessor;

/** Lifecycle boundary between operational message processing and Bungee delivery policy. */
interface BungeeNotificationSink extends AutoCloseable {
    void accept(BungeeMessageProcessor.ProcessingResult result);

    boolean dispatchTest(String sourceKey);

    @Override
    void close();
}
