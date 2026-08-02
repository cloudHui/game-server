package utils.other;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

public class Base64Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Base64Utils.class);

    public Base64Utils() {
    }

    public static String encoder(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] decoder(String data) {
        try {
            return Base64.getDecoder().decode(data);
        } catch (Exception e) {
            LOGGER.error("failed for decoder({})", data, e);
            return null;
        }
    }
}
