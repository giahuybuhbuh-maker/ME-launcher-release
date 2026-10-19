package launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Khoi chay 1 tien trinh (process) ben ngoai va theo doi output/exit code
 * cua no. Lop nay KHONG biet gi ve Minecraft - chi la tien ich chung de
 * chay va theo doi 1 tien trinh, MinecraftLauncher se dung no de thuc su
 * chay game sau khi da chuan bi xong lenh.
 */
public class ProcessManager {

    /**
     * Chay lenh da cho, dua output (gop ca stdout+stderr, giong console
     * that) qua onOutput theo tung dong, roi goi onExit khi tien trinh ket
     * thuc voi exit code. HAM NAY CHAN (block) cho toi khi tien trinh dong
     * lai - phai goi tren luong nen (vd javafx.concurrent.Task), khong goi
     * truc tiep tren FX Application Thread.
     */
    public void runAndWait(List<String> command, Path workingDirectory,
                            Consumer<String> onOutput, IntConsumer onExit) throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true); // gop stderr vao stdout, doc 1 luong duy nhat

        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (onOutput != null) onOutput.accept(line);
            }
        }

        int exitCode = process.waitFor();
        if (onExit != null) onExit.accept(exitCode);
    }
}
