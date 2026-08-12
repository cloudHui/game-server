package web.photo;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

import static org.junit.Assert.*;

public class WebpImageIoTest {
    @Test public void openSourcePluginCanEncodeAndDecodeWebp() throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        assertTrue("WebP writer not registered", writers.hasNext());
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
        assertTrue("WebP reader not registered", readers.hasNext());

        File file = File.createTempFile("photo-library-", ".webp");
        try {
            BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
            source.setRGB(1, 1, 0x336699);
            assertTrue(ImageIO.write(source, "webp", file));
            BufferedImage decoded = ImageIO.read(file);
            assertNotNull(decoded);
            assertEquals(3, decoded.getWidth());
            assertEquals(2, decoded.getHeight());
        } finally {
            file.delete();
        }
    }
}
