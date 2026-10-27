package dev.auralink.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Properties;

/** Generated locally on first launch: never put your personal code into GitHub. */
public final class BridgePairing {
    private final String key;
    public BridgePairing(Path config) {
        Path file = config.resolve("auralink-pairing.properties");
        Properties p = new Properties();
        try {
            if (Files.isRegularFile(file)) {
                try (InputStream input = Files.newInputStream(file)) { p.load(input); }
            }
        } catch (IOException ignored) { }
        String token = p.getProperty("key", "");
        if (!token.matches("[0-9a-f]{32}")) {
            byte[] bytes = new byte[16];
            new SecureRandom().nextBytes(bytes);
            token = HexFormat.of().formatHex(bytes);
            p.setProperty("key", token);
            try {
                Files.createDirectories(file.getParent());
                try (OutputStream output = Files.newOutputStream(file)) {
                    p.store(output, "Private AuraLink local browser pairing secret. Do not publish.");
                }
            } catch (IOException ignored) { }
        }
        key = token;
    }
    public String key() { return key; }
}
