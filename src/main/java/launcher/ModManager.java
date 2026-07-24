package launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Quan ly file mod (.jar) trong game/mods/. KHONG phu thuoc mod loader nao
 * ca (Fabric/Forge/NeoForge deu doc chung thu muc mods/ giong nhau) - day
 * chi la thao tac file don gian: liet ke, bat/tat, them, xoa.
 *
 * "Tat" 1 mod = doi duoi .jar -> .jar.disabled (cach lam pho bien, mod
 * loader se khong thay file nay nen bo qua, khong can xoa hay di dau ca).
 */
public class ModManager {

    public record ModInfo(String fileName, boolean enabled, long sizeBytes) {}

    public List<ModInfo> listMods() throws IOException {
        Path modsDir = FileManager.modsDir();
        if (!Files.exists(modsDir)) return List.of();

        List<ModInfo> mods = new ArrayList<>();
        try (Stream<Path> stream = Files.list(modsDir)) {
            for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                String name = path.getFileName().toString();
                if (name.endsWith(".jar") || name.endsWith(".jar.disabled")) {
                    mods.add(new ModInfo(name, name.endsWith(".jar"), Files.size(path)));
                }
            }
        }
        return mods;
    }

    /** Bat/tat 1 mod bang cach doi duoi file .jar <-> .jar.disabled. */
    public void setModEnabled(String fileName, boolean enabled) throws IOException {
        Path modsDir = FileManager.modsDir();
        String baseName = fileName.endsWith(".disabled")
                ? fileName.substring(0, fileName.length() - ".disabled".length())
                : fileName;
        // baseName gio la "xxx.jar"
        String withoutJar = baseName.substring(0, baseName.length() - ".jar".length());

        Path source = modsDir.resolve(fileName);
        Path target = modsDir.resolve(withoutJar + (enabled ? ".jar" : ".jar.disabled"));

        if (!source.equals(target)) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Copy 1 file .jar tu ngoai vao game/mods/. */
    public Path addMod(Path sourceJarFile) throws IOException {
        Path modsDir = FileManager.modsDir();
        Files.createDirectories(modsDir);
        Path dest = modsDir.resolve(sourceJarFile.getFileName());
        Files.copy(sourceJarFile, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    public void removeMod(String fileName) throws IOException {
        Files.deleteIfExists(FileManager.modsDir().resolve(fileName));
    }
}
