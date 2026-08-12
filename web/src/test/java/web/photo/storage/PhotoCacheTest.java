package web.photo.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import web.photo.config.PhotoProperties;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class PhotoCacheTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void evictsLeastRecentlyUsedFileFromDisk() throws Exception {
        PhotoProperties properties = new PhotoProperties();
        properties.setCacheDir(temporary.newFolder("cache").getAbsolutePath());
        properties.setCacheMaxFiles(2);
        PhotoCache cache = new PhotoCache(properties);
        cache.init();

        Path first = cache.target(1, "jpg");
        Path second = cache.target(2, "jpg");
        Path third = cache.target(3, "jpg");
        Files.write(first, new byte[]{1}); cache.commit(1, first);
        Files.write(second, new byte[]{2}); cache.commit(2, second);
        assertNotNull(cache.get(1));
        Files.write(third, new byte[]{3}); cache.commit(3, third);

        assertTrue(Files.exists(first));
        assertFalse(Files.exists(second));
        assertTrue(Files.exists(third));
        assertEquals(2, cache.size());
    }

    @Test public void keepsLeasedFileUntilTransferCompletes() throws Exception {
        PhotoProperties properties = new PhotoProperties();
        properties.setCacheDir(temporary.newFolder("leased-cache").getAbsolutePath());
        PhotoCache cache = new PhotoCache(properties);
        cache.init();
        Path file = cache.target(7, "webp");
        Files.write(file, new byte[]{7});
        cache.commit(7, file);

        PhotoCache.Lease lease = cache.acquire(7);
        cache.remove(7);
        assertTrue(Files.exists(file));
        lease.close();
        assertFalse(Files.exists(file));
    }
}
