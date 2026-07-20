package network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.LongConsumer;

/**
 * Tai 1 file tu URL ve dich, co xac thuc checksum SHA-1 (Mojang cung cap
 * sha1 cho hau het file - libraries, assets, client.jar...) de dam bao
 * file tai ve khong bi hong/thieu.
 */
public class Downloader {

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

    /**
     * @param url          dia chi file can tai
     * @param destination  duong dan luu file sau khi tai xong
     * @param expectedSha1 sha1 ky vong (co the null neu khong can kiem tra)
     * @param onProgress   goi lai voi so byte da tai (co the null neu khong can theo doi)
     */
    public void download(String url, Path destination, String expectedSha1, LongConsumer onProgress)
            throws IOException, InterruptedException {

        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path tempFile = destination.resolveSibling(destination.getFileName() + ".part");

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " khi tai " + url);
        }

        long total = 0;
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
                if (onProgress != null) onProgress.accept(total);
            }
        }

        if (expectedSha1 != null && !expectedSha1.isBlank() && !verifySha1(tempFile, expectedSha1)) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Sai checksum SHA-1 sau khi tai: " + url);
        }

        Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean verifySha1(Path file, String expectedSha1) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            return actual.equalsIgnoreCase(expectedSha1);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("May khong ho tro SHA-1 (khong nen xay ra)", e);
        }
    }
}
