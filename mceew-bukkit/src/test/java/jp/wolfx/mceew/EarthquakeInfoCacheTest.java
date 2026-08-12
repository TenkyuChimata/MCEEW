package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthquakeInfoCacheTest {
    private static final String MD5_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String MD5_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void firstJmaSnapshotIsStoredWithoutNotification() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        EarthquakeInfoCache.JmaSnapshot snapshot = jma(MD5_A, "first");

        EarthquakeInfoCache.UpdateResult result = cache.updateJma(snapshot);

        assertEquals(EarthquakeInfoCache.UpdateResult.FIRST_VALUE, result);
        assertFalse(result.shouldNotify(true));
        assertSame(snapshot, cache.getJma());
    }

    @Test
    void firstCencSnapshotIsStoredWithoutNotification() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        EarthquakeInfoCache.CencSnapshot snapshot = cenc(MD5_A, "first");

        EarthquakeInfoCache.UpdateResult result = cache.updateCenc(snapshot);

        assertEquals(EarthquakeInfoCache.UpdateResult.FIRST_VALUE, result);
        assertFalse(result.shouldNotify(true));
        assertSame(snapshot, cache.getCenc());
    }

    @Test
    void emptyJmaMd5IsRejectedWithoutReplacingTheCurrentSnapshot() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        EarthquakeInfoCache.JmaSnapshot current = jma(MD5_A, "current");
        cache.updateJma(current);

        assertThrows(IllegalArgumentException.class,
                () -> cache.updateJma(jma(" ", "invalid")));
        assertSame(current, cache.getJma());
    }

    @Test
    void nullCencMd5IsRejectedWithoutReplacingTheCurrentSnapshot() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        EarthquakeInfoCache.CencSnapshot current = cenc(MD5_A, "current");
        cache.updateCenc(current);

        assertThrows(IllegalArgumentException.class,
                () -> cache.updateCenc(cenc(null, "invalid")));
        assertSame(current, cache.getCenc());
    }

    @Test
    void sameJmaMd5ReplayIsUnchangedAndSilent() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateJma(jma(MD5_A, "initial"));

        EarthquakeInfoCache.UpdateResult result = cache.updateJma(jma(MD5_A, "replayed"));

        assertEquals(EarthquakeInfoCache.UpdateResult.UNCHANGED, result);
        assertFalse(result.shouldNotify(true));
        assertEquals("replayed", cache.getJma().region);
    }

    @Test
    void sameCencMd5ReplayIsUnchangedAndSilent() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateCenc(cenc(MD5_A, "initial"));

        EarthquakeInfoCache.UpdateResult result = cache.updateCenc(cenc(MD5_A, "replayed"));

        assertEquals(EarthquakeInfoCache.UpdateResult.UNCHANGED, result);
        assertFalse(result.shouldNotify(true));
        assertEquals("replayed", cache.getCenc().region);
    }

    @Test
    void changedJmaMd5NotifiesExactlyOnce() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateJma(jma(MD5_A, "old"));
        AtomicInteger notifications = new AtomicInteger();

        notifyIfChanged(cache.updateJma(jma(MD5_B, "new")), true, notifications);
        notifyIfChanged(cache.updateJma(jma(MD5_B, "duplicate")), true, notifications);

        assertEquals(1, notifications.get());
        assertEquals(MD5_B, cache.getJma().md5);
    }

    @Test
    void changedCencMd5NotifiesExactlyOnce() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateCenc(cenc(MD5_A, "old"));
        AtomicInteger notifications = new AtomicInteger();

        notifyIfChanged(cache.updateCenc(cenc(MD5_B, "new")), true, notifications);
        notifyIfChanged(cache.updateCenc(cenc(MD5_B, "duplicate")), true, notifications);

        assertEquals(1, notifications.get());
        assertEquals(MD5_B, cache.getCenc().md5);
    }

    @Test
    void reloadReplayKeepsInfoAvailableWithoutNotification() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateJma(jma(MD5_A, "before reload"));
        cache.updateCenc(cenc(MD5_A, "before reload"));

        EarthquakeInfoCache.UpdateResult jma = cache.updateJma(jma(MD5_A, "after reload"));
        EarthquakeInfoCache.UpdateResult cenc = cache.updateCenc(cenc(MD5_A, "after reload"));

        assertEquals(EarthquakeInfoCache.UpdateResult.UNCHANGED, jma);
        assertEquals(EarthquakeInfoCache.UpdateResult.UNCHANGED, cenc);
        assertFalse(jma.shouldNotify(true));
        assertFalse(cenc.shouldNotify(true));
        assertTrue(cache.formatJma("%region%").contains("after reload"));
        assertTrue(cache.formatCenc("%region%").contains("after reload"));
    }

    @Test
    void reconnectWithSameSnapshotsIsSilent() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateJma(jma(MD5_A, "connected"));
        cache.updateCenc(cenc(MD5_A, "connected"));

        assertEquals(EarthquakeInfoCache.UpdateResult.UNCHANGED,
                cache.updateJma(jma(MD5_A, "reconnected")));
        assertEquals(EarthquakeInfoCache.UpdateResult.UNCHANGED,
                cache.updateCenc(cenc(MD5_A, "reconnected")));
    }

    @Test
    void reconnectWithNewSnapshotsNotifiesOncePerSource() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateJma(jma(MD5_A, "before disconnect"));
        cache.updateCenc(cenc(MD5_A, "before disconnect"));

        EarthquakeInfoCache.UpdateResult jma = cache.updateJma(jma(MD5_B, "during disconnect"));
        EarthquakeInfoCache.UpdateResult cenc = cache.updateCenc(cenc(MD5_B, "during disconnect"));

        assertEquals(EarthquakeInfoCache.UpdateResult.CHANGED, jma);
        assertEquals(EarthquakeInfoCache.UpdateResult.CHANGED, cenc);
        assertTrue(jma.shouldNotify(true));
        assertTrue(cenc.shouldNotify(true));
        assertEquals(MD5_B, cache.getJma().md5);
        assertEquals(MD5_B, cache.getCenc().md5);
    }

    @Test
    void concurrentIdenticalUpdatesProduceAtMostOneChangedResult() throws Exception {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateJma(jma(MD5_A, "old"));
        cache.updateCenc(cenc(MD5_A, "old"));
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger jmaChanges = new AtomicInteger();
        AtomicInteger cencChanges = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable update = () -> {
            await(start, failure);
            if (cache.updateJma(jma(MD5_B, "new")) == EarthquakeInfoCache.UpdateResult.CHANGED) {
                jmaChanges.incrementAndGet();
            }
            if (cache.updateCenc(cenc(MD5_B, "new")) == EarthquakeInfoCache.UpdateResult.CHANGED) {
                cencChanges.incrementAndGet();
            }
        };
        Thread first = new Thread(update);
        Thread second = new Thread(update);
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();

        assertEquals(1, jmaChanges.get());
        assertEquals(1, cencChanges.get());
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
    }

    @Test
    void disabledActionsStillUpdateBothCachesWithoutNotification() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        cache.updateJma(jma(MD5_A, "old"));
        cache.updateCenc(cenc(MD5_A, "old"));

        EarthquakeInfoCache.UpdateResult jma = cache.updateJma(jma(MD5_B, "new"));
        EarthquakeInfoCache.UpdateResult cenc = cache.updateCenc(cenc(MD5_B, "new"));

        assertFalse(jma.shouldNotify(false));
        assertFalse(cenc.shouldNotify(false));
        assertEquals(MD5_B, cache.getJma().md5);
        assertEquals(MD5_B, cache.getCenc().md5);
    }

    @Test
    void infoReadsCurrentSnapshotsRegardlessOfNotificationSuppression() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        assertEquals(EarthquakeInfoCache.NOT_AVAILABLE, cache.formatJma("unused"));
        assertEquals(EarthquakeInfoCache.NOT_AVAILABLE, cache.formatCenc("unused"));

        cache.updateJma(jma(MD5_A, "jma info"));
        cache.updateCenc(cenc(MD5_A, "cenc info"));
        EarthquakeInfoCache.UpdateResult jma = cache.updateJma(jma(MD5_B, "new jma info"));
        EarthquakeInfoCache.UpdateResult cenc = cache.updateCenc(cenc(MD5_B, "new cenc info"));

        assertFalse(jma.shouldNotify(false));
        assertFalse(cenc.shouldNotify(false));
        assertEquals("new jma info", cache.formatJma("%region%"));
        assertEquals("new cenc info", cache.formatCenc("%region%"));
    }

    @Test
    void cencEqlistResponseCreatesAnImmediatelyReadableImmutableSnapshot() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        JsonObject response = JsonParser.parseString("{"
                + "\"type\":\"cenc_eqlist\","
                + "\"md5\":\"cccccccccccccccccccccccccccccccc\","
                + "\"No1\":{"
                + "\"type\":\"reviewed\","
                + "\"time\":\"2026-08-09 12:34:56\","
                + "\"location\":\"test region\","
                + "\"magnitude\":\"5.2\","
                + "\"depth\":\"10\","
                + "\"latitude\":\"30.1\","
                + "\"longitude\":\"120.2\","
                + "\"intensity\":\"6\"}}")
                .getAsJsonObject();

        EarthquakeInfoCache.UpdateResult result = cache.updateCenc(
                EarthquakeInfoCache.CencSnapshot.fromEqlist(
                        response, "formatted time", "§26"));

        assertEquals(EarthquakeInfoCache.UpdateResult.FIRST_VALUE, result);
        assertFalse(result.shouldNotify(true));
        assertEquals("正式测定|formatted time|test region|5.2|10km|30.1|120.2|§26",
                cache.formatCenc(
                        "%flag%|%origin_time%|%region%|%mag%|%depth%|%lat%|%lon%|%shindo%"));
        assertEquals("cccccccccccccccccccccccccccccccc", cache.getCenc().md5);
    }

    @Test
    void immutableJmaAndCencSnapshotsAreSafelyPublishedAcrossThreads() throws Exception {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            await(start, failure);
            for (int index = 0; index < 50_000 && failure.get() == null; index++) {
                String value = String.format("%032x", index);
                cache.updateJma(jma(value, value));
                cache.updateCenc(cenc(value, value));
            }
        });
        Thread reader = new Thread(() -> {
            await(start, failure);
            for (int index = 0; index < 100_000 && failure.get() == null; index++) {
                EarthquakeInfoCache.JmaSnapshot jma = cache.getJma();
                if (jma != null) {
                    assertSnapshot(jma.md5, jma.originTime, jma.region, jma.magnitude,
                            jma.depth, jma.latitude, jma.longitude, jma.displayIntensity, jma.info,
                            failure);
                }
                EarthquakeInfoCache.CencSnapshot cenc = cache.getCenc();
                if (cenc != null) {
                    assertSnapshot(cenc.md5, cenc.type, cenc.originTime, cenc.region,
                            cenc.magnitude, cenc.depth, cenc.latitude, cenc.longitude,
                            cenc.displayIntensity, failure);
                }
            }
        });

        writer.start();
        reader.start();
        start.countDown();
        writer.join();
        reader.join();

        assertNull(failure.get(), () -> String.valueOf(failure.get()));
    }

    private static EarthquakeInfoCache.JmaSnapshot jma(String md5, String value) {
        return new EarthquakeInfoCache.JmaSnapshot(
                md5, value, value, value, value, value, value, value, value);
    }

    private static EarthquakeInfoCache.CencSnapshot cenc(String md5, String value) {
        return new EarthquakeInfoCache.CencSnapshot(
                md5, value, value, value, value, value, value, value, value);
    }

    private static void notifyIfChanged(EarthquakeInfoCache.UpdateResult result, boolean enabled,
                                        AtomicInteger notifications) {
        if (result.shouldNotify(enabled)) {
            notifications.incrementAndGet();
        }
    }

    private static void assertSnapshot(String expected, String first, String second, String third,
                                       String fourth, String fifth, String sixth, String seventh,
                                       String eighth, AtomicReference<Throwable> failure) {
        try {
            assertEquals(expected, first);
            assertEquals(expected, second);
            assertEquals(expected, third);
            assertEquals(expected, fourth);
            assertEquals(expected, fifth);
            assertEquals(expected, sixth);
            assertEquals(expected, seventh);
            assertEquals(expected, eighth);
        } catch (Throwable error) {
            failure.compareAndSet(null, error);
        }
    }

    private static void await(CountDownLatch latch, AtomicReference<Throwable> failure) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, error);
        }
    }
}
