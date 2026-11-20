package network;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Tai NHIEU file cung luc bang thread pool - dung cho DownloadManager
 * (Phase 3) khi can tai hang tram file libraries/assets cua 1 phien ban
 * Minecraft cung mot luc, thay vi tai tuan tu tung file rat cham.
 */
public class FileDownloader {

    /** expectedSize <= 0 nghia la khong biet truoc kich thuoc (vd thu vien kieu Fabric). */
    public record DownloadTask(String url, Path destination, String expectedSha1, long expectedSize) {}

    private final Downloader downloader = new Downloader();
    private final int threadCount;

    public FileDownloader() {
        this(4);
    }

    public FileDownloader(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Tai tat ca task trong danh sach, goi onFileDone(url, loi) sau MOI file
     * (loi = null neu tai thanh cong). Ham nay cho toi khi tai xong het
     * (hoac het 1 tieng) roi moi tra ve, nen goi tren luong nen, khong goi
     * truc tiep tren luong giao dien JavaFX.
     */
    public void downloadAll(List<DownloadTask> tasks, BiConsumer<String, Exception> onFileDone)
            throws InterruptedException {

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (DownloadTask task : tasks) {
            pool.submit(() -> {
                try {
                    downloader.download(task.url(), task.destination(), task.expectedSha1(), null);
                    onFileDone.accept(task.url(), null);
                } catch (IOException | InterruptedException e) {
                    onFileDone.accept(task.url(), e);
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.HOURS);
    }
}
