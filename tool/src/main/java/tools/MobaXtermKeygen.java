package tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MobaXtermKeygen {

    private static final String VARIANT_BASE64_TABLE =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    private static final char[] VARIANT_BASE64_CHARS = VARIANT_BASE64_TABLE.toCharArray();

    public enum LicenseType {
        Professional(1), Educational(3), Personal(4);

        private final int value;

        LicenseType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // 对应 Python 的 VariantBase64Encode (小端序读取 3 字节并转换成 4 个变体 Base64 字符)
    public static byte[] variantBase64Encode(byte[] bs) {
        int blocksCount = bs.length / 3;
        int leftBytes = bs.length % 3;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < blocksCount; i++) {
            int codingInt = ((bs[3 * i] & 0xFF)) | ((bs[3 * i + 1] & 0xFF) << 8) | ((bs[3 * i + 2] & 0xFF) << 16);

            result.append(VARIANT_BASE64_CHARS[codingInt & 0x3f]);
            result.append(VARIANT_BASE64_CHARS[(codingInt >> 6) & 0x3f]);
            result.append(VARIANT_BASE64_CHARS[(codingInt >> 12) & 0x3f]);
            result.append(VARIANT_BASE64_CHARS[(codingInt >> 18) & 0x3f]);
        }

        if (leftBytes == 0) {
            return result.toString().getBytes(StandardCharsets.US_ASCII);
        } else if (leftBytes == 1) {
            int codingInt = (bs[3 * blocksCount] & 0xFF);
            result.append(VARIANT_BASE64_CHARS[codingInt & 0x3f]);
            result.append(VARIANT_BASE64_CHARS[(codingInt >> 6) & 0x3f]);
        } else { // leftBytes == 2
            int codingInt = ((bs[3 * blocksCount] & 0xFF)) | ((bs[3 * blocksCount + 1] & 0xFF) << 8);
            result.append(VARIANT_BASE64_CHARS[codingInt & 0x3f]);
            result.append(VARIANT_BASE64_CHARS[(codingInt >> 6) & 0x3f]);
            result.append(VARIANT_BASE64_CHARS[(codingInt >> 12) & 0x3f]);
        }

        return result.toString().getBytes(StandardCharsets.US_ASCII);
    }

    // 对应 Python 的 EncryptBytes
    public static byte[] encryptBytes(int key, byte[] bs) {
        byte[] result = new byte[bs.length];
        for (int i = 0; i < bs.length; i++) {
            int b = bs[i] ^ ((key >> 8) & 0xff);
            result[i] = (byte)b;
            key = (b & 0xFF) & key | 0x482D;
        }
        return result;
    }

    // 生成授权并打包为 Custom.mxtpro
    public static void generateLicense(LicenseType type, int count, String userName, int majorVersion,
        int minorVersion) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must be >= 0");
        }

        // 格式化 License 字符串
        // 对应 Python: '%d#%s|%d%d#%d#%d3%d6%d#%d#%d#%d#'
        String licenseString = String.format("%d#%s|%d%d#%d#%d3%d6%d#%d#%d#%d#", type.getValue(), userName,
            majorVersion, minorVersion, count, majorVersion, minorVersion, minorVersion, 0, // Unknown
            0, // No Games flag
            0 // No Plugins flag
        );

        // 加密并进行变体 Base64 编码
        byte[] encrypted = encryptBytes(0x787, licenseString.getBytes(StandardCharsets.UTF_8));
        byte[] encodedBytes = variantBase64Encode(encrypted);
        String encodedLicenseString = new String(encodedBytes, StandardCharsets.US_ASCII);

        // 写入 Custom.mxtpro 压缩包中名为 Pro.key 的文件
        File zipFile = new File("Custom.mxtpro");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            ZipEntry entry = new ZipEntry("Pro.key");
            zos.putNextEntry(entry);
            zos.write(encodedLicenseString.getBytes(StandardCharsets.US_ASCII));
            zos.closeEntry();

            System.out.println("[*] Success!");
            System.out.println("[*] File generated: " + zipFile.getAbsolutePath());
            System.out.println("[*] Please move or copy the newly-generated file to MobaXterm's installation path.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("    java MobaXtermKeygen <UserName> <Version>");
        System.out.println();
        System.out.println("    <UserName>:      The Name licensed to");
        System.out.println("    <Version>:       The Version of MobaXterm");
        System.out.println("                     Example:    10.9");
        System.out.println();
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            printHelp();
            System.exit(0);
        } else {
            String userName = args[0];
            String[] versionParts = args[1].split("\\.");
            int majorVersion = Integer.parseInt(versionParts[0]);
            int minorVersion = Integer.parseInt(versionParts[1]);

            generateLicense(LicenseType.Professional, 1, userName, majorVersion, minorVersion);
        }
    }
}