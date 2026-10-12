package ee.ahjusepad.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Salvestab klientide päringud kohalikku JSONL-faili.
 * Iga päring lisatakse eraldi reale; olemasolevaid kirjeid üle ei kirjutata.
 */
public final class InquiryStore {
    private final Path file;
    private final Object lock = new Object();

    public InquiryStore(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        // Fail asub DATA_DIR kaustas (vaikimisi projekti data-kaustas).
        // Käivitamisel luuakse nii kaust kui ka fail, kui neid veel pole.
        this.file = dataDirectory.resolve("inquiries.jsonl").toAbsolutePath().normalize();
        if (!this.file.startsWith(dataDirectory.toAbsolutePath().normalize())) {
            throw new IOException("Invalid data path");
        }
        if (!Files.exists(file)) Files.createFile(file);
        restrictPermissions(file);
    }

    public Map<String, String> save(String name, String email, String phone,
                                    String interest, String message) throws IOException {
        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        Map<String, String> record = Map.of(
                "id", id,
                "createdAt", createdAt,
                "name", name,
                "email", email,
                "phone", phone,
                "interest", interest,
                "message", message
        );
        String line = Json.value(record) + System.lineSeparator();
        // Lukustus tagab, et samaaegsed päringud kirjutatakse faili terviklike ridadena.
        synchronized (lock) {
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }
        return record;
    }

    public List<String> allJsonLines() throws IOException {
        synchronized (lock) {
            List<String> result = new ArrayList<>();
            // Tühjad read jäetakse vahele, et administraatoriliidese vastus oleks korrektne JSON-massiiv.
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) result.add(line);
            }
            return result;
        }
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ja mõned võrgukettad ei toeta POSIX-õiguseid; seal jäävad kehtima süsteemi õigused.
        }
    }
}
