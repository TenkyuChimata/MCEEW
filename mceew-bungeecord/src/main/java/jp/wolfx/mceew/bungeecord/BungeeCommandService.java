package jp.wolfx.mceew.bungeecord;

import java.util.function.Consumer;

interface BungeeCommandService {
    String latestJmaEarthquakeInformation();

    String latestCencEarthquakeInformation();

    boolean dispatchTest(String sourceKey);

    void requestReload(Consumer<BungeePluginShell.ReloadOutcome> completion);
}
