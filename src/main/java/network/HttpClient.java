package network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Wrapper mong quanh java.net.http.HttpClient (co san trong JDK) de goi
 * GET va lay ve chuoi (thuong la JSON, vd version manifest cua Mojang).
 *
 * Luu y: lop nay trung ten voi java.net.http.HttpClient cua JDK, nen ben
 * duoi phai dung ten day du (java.net.http.HttpClient) thay vi import,
 * neu khong se bi xung dot ten voi chinh class nay.
 */
public class HttpClient {

    private final java.net.http.HttpClient client;

    public HttpClient() {
        this.client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Goi GET toi url, tra ve noi dung dang chuoi. Nem loi neu status code khac 200. */
    public String getString(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " khi goi " + url);
        }
        return response.body();
    }
}
