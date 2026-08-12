package web.photo.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.web.multipart.MultipartFile;
import web.photo.config.PhotoProperties;
import web.photo.repository.PhotoRepository;
import web.photo.storage.PhotoCache;
import web.service.UserService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

public class PhotoServiceIntegrationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void uploadsArchivesAndReadsPhotoWithoutFileTimestamp() throws Exception {
        File root = temporary.newFolder("photos");
        PhotoProperties properties = properties(root);
        PhotoRepository repository = new PhotoRepository(properties);
        repository.init();
        PhotoCache cache = new PhotoCache(properties);
        cache.init();
        PhotoService service = new PhotoService(properties, repository, cache);
        byte[] original = png();
        MultipartFile upload = multipart(original);
        UserService.UserInfo user = new UserService.UserInfo(
                "sid", 42, "family", "家人", "token",
                Collections.emptyList(), Collections.emptyList());

        Map<String, Object> saved = service.upload(upload, user);
        long id = ((Number) saved.get("id")).longValue();
        Map<String, Object> page = service.list(user, 1, 24, null);
        assertEquals(1L, page.get("total"));
        assertTrue(service.thumbnail(id, user).length > 0);

        PhotoService.Original highResolution = service.original(id, user);
        try {
            assertArrayEquals(original, Files.readAllBytes(highResolution.lease.getPath()));
        } finally {
            highResolution.lease.close();
        }
        assertTrue(new File(root, "photos.sqlite").isFile());
        assertTrue(hasZip(new File(root, "archives")));
    }

    private PhotoProperties properties(File root) {
        PhotoProperties p = new PhotoProperties();
        p.setDataDir(root.getAbsolutePath());
        p.setArchiveDir(new File(root, "archives").getAbsolutePath());
        p.setThumbnailDir(new File(root, "thumbnails").getAbsolutePath());
        p.setCacheDir(new File(root, "cache").getAbsolutePath());
        p.setStagingDir(new File(root, "staging").getAbsolutePath());
        return p;
    }

    private byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);
        image.setRGB(4, 3, 0x2255aa);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private MultipartFile multipart(byte[] bytes) {
        return new MultipartFile() {
            public String getName() { return "files"; }
            public String getOriginalFilename() { return "山川.png"; }
            public String getContentType() { return "image/png"; }
            public boolean isEmpty() { return false; }
            public long getSize() { return bytes.length; }
            public byte[] getBytes() { return bytes; }
            public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), bytes); }
        };
    }

    private boolean hasZip(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory() && hasZip(file)) return true;
            if (file.getName().endsWith(".zip")) return true;
        }
        return false;
    }
}
