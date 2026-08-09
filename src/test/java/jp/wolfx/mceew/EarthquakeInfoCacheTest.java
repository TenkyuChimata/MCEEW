package jp.wolfx.mceew;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EarthquakeInfoCacheTest {
    @Test
    void cencInfoHasAnExplicitMessageBeforeTheFirstSnapshot() {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();

        assertEquals(EarthquakeInfoCache.NOT_AVAILABLE, cache.formatCenc("unused"));
        assertEquals("[MCEEW] Earthquake information is not available yet.",
                cache.formatCenc("unused"));

        cache.setCenc(new EarthquakeInfoCache.CencSnapshot(
                "md5", "reviewed", "time", "region", "5.0", "10km",
                "1.0", "2.0", "§a5"));
        assertEquals("reviewed|time|region|5.0|10km|1.0|2.0|§a5",
                cache.formatCenc("%flag%|%origin_time%|%region%|%mag%|%depth%|%lat%|%lon%|%shindo%"));
    }

    @Test
    void immutableJmaAndCencSnapshotsAreSafelyPublishedAcrossThreads() throws Exception {
        EarthquakeInfoCache cache = new EarthquakeInfoCache();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            await(start, failure);
            for (int index = 0; index < 50_000 && failure.get() == null; index++) {
                String value = Integer.toString(index);
                cache.setJma(new EarthquakeInfoCache.JmaSnapshot(
                        value, value, value, value, value, value, value, value, value));
                cache.setCenc(new EarthquakeInfoCache.CencSnapshot(
                        value, value, value, value, value, value, value, value, value));
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
