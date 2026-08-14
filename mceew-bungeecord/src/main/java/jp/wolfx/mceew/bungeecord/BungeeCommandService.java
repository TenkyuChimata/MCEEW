package jp.wolfx.mceew.bungeecord;

import java.util.function.Consumer;

interface BungeeCommandService {
    enum TestOutcome {
        DISPATCHED,
        IN_PROGRESS,
        UNAVAILABLE,
        FAILED
    }

    String latestJmaEarthquakeInformation();

    String latestCencEarthquakeInformation();

    TestOutcome dispatchTest(String sourceKey);

    void requestReload(Consumer<BungeePluginShell.ReloadOutcome> completion);
}
