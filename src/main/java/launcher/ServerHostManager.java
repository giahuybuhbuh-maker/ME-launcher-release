package launcher;

import com.simtechdata.waifupnp.UPnP;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public class ServerHostManager {
    private Process serverProcess;
    private BufferedWriter commandWriter;
    private int currentPort = 25565;

    /**
     * Khởi chạy Server Minecraft kèm Aikar's Flags và mở cổng UPnP
     */
    public void startServer(Path serverDir, Path serverJar, String javaPath, int ramMb, int port, Consumer<String> onLog) throws IOException {
        if (isRunning()) {
            onLog.accept("[ME-Server] Server đang chạy rồi!");
            return;
        }
        this.currentPort = port;

        // 1. Tự động đồng ý EULA để Server không bị tắt ngay lần đầu
        Path eulaFile = serverDir.resolve("eula.txt");
        Files.writeString(eulaFile, "eula=true\n", StandardCharsets.UTF_8);

        // 2. Mở cổng Router tự động bằng WaifUPnP (chạy bất đồng bộ để không đơ UI)
        new Thread(() -> {
            onLog.accept("[UPnP] Đang kiểm tra hỗ trợ UPnP trên Router...");
            if (UPnP.isUPnPAvailable()) {
                boolean tcpOk = UPnP.openPortTCP(port);
                boolean udpOk = UPnP.openPortUDP(port);
                if (tcpOk || udpOk) {
                    onLog.accept("[UPnP] Đã mở thành công cổng " + port + "! IP Public của bạn: " + UPnP.getExternalIP());
                } else {
                    onLog.accept("[UPnP] Router có UPnP nhưng từ chối mở cổng " + port + ".");
                }
            } else {
                onLog.accept("[UPnP] Modem mạng không bật UPnP (Chỉ chơi được mạng LAN hoặc qua Radmin/Tailscale).");
            }
        }, "UPnP-Thread").start();

        // 3. Xây dựng lệnh khởi chạy với ProcessBuilder + Aikar's Flags
        List<String> cmd = new ArrayList<>();
        cmd.add(javaPath);
        cmd.add("-Xms" + ramMb + "M");
        cmd.add("-Xmx" + ramMb + "M");

        // Bộ Aikar's Flags chuẩn tối ưu Garbage Collection cho Server Minecraft
        Collections.addAll(cmd,
            "-XX:+UseG1GC",
            "-XX:+ParallelRefProcEnabled",
            "-XX:MaxGCPauseMillis=200",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+DisableExplicitGC",
            "-XX:+AlwaysPreTouch",
            "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=40",
            "-XX:G1HeapRegionSize=8M",
            "-XX:G1ReservePercent=20",
            "-XX:G1HeapWastePercent=5",
            "-XX:G1MixedGCCountTarget=4",
            "-XX:InitiatingHeapOccupancyPercent=15",
            "-XX:G1MixedGCLiveThresholdPercent=90",
            "-XX:G1RSetUpdatingPauseTimePercent=5",
            "-XX:SurvivorRatio=32",
            "-XX:+PerfDisableSharedMem",
            "-XX:MaxTenuringThreshold=1",
            "-Dusing.aikars.flags=https://mcflags.emc.gs",
            "-Daikars.new.flags=true"
        );

        cmd.add("-jar");
        cmd.add(serverJar.toAbsolutePath().toString());
        cmd.add("nogui"); // Tắt cửa sổ Swing mặc định của Minecraft để nhẹ máy

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true); // Gộp cả luồng lỗi và luồng thông báo vào làm một

        serverProcess = pb.start();
        commandWriter = new BufferedWriter(new OutputStreamWriter(serverProcess.getOutputStream(), StandardCharsets.UTF_8));

        // 4. Luồng đọc Log thời gian thực đẩy lên giao diện ME Launcher
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    onLog.accept(line);
                }
            } catch (IOException e) {
                onLog.accept("[ME-Server] Luồng đọc log đã đóng.");
            } finally {
                closeUPnP(onLog);
                onLog.accept("[ME-Server] Server đã dừng hoàn toàn.");
            }
        }, "Server-Log-Reader").start();
    }

    /**
     * Gửi lệnh từ ô nhập lệnh của Launcher vào thẳng Console của Server
     */
    public void sendCommand(String command) {
        if (isRunning() && commandWriter != null) {
            try {
                commandWriter.write(command);
                commandWriter.newLine();
                commandWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Dừng Server an toàn (lưu world trước khi tắt) và đóng cổng UPnP
     */
    public void stopServer() {
        if (isRunning()) {
            sendCommand("stop");
        }
    }

    private void closeUPnP(Consumer<String> onLog) {
        if (UPnP.isMappedTCP(currentPort)) {
            UPnP.closePortTCP(currentPort);
            UPnP.closePortUDP(currentPort);
            onLog.accept("[UPnP] Đã đóng cổng " + currentPort + " an toàn.");
        }
    }

    public boolean isRunning() {
        return serverProcess != null && serverProcess.isAlive();
    }
}
