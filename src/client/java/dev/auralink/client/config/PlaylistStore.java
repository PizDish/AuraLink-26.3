package dev.auralink.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Persistent user playlists. IDs are stored as separate indexed properties (no delimiters in IDs). */
public final class PlaylistStore {
    private final Path file;
    private final Map<String, List<String>> playlists = new LinkedHashMap<>();

    public PlaylistStore(Path configDir) {
        this.file = configDir.resolve("auralink-playlists.properties");
        read();
        if (playlists.isEmpty()) {
            playlists.put("My Mix", new ArrayList<>());
            playlists.put("Focus", new ArrayList<>());
            playlists.put("Adventure", new ArrayList<>());
            save();
        }
    }

    public synchronized Set<String> names() { return new LinkedHashSet<>(playlists.keySet()); }
    public synchronized List<String> tracks(String name) {
        return List.copyOf(playlists.getOrDefault(name, Collections.emptyList()));
    }
    public synchronized boolean create(String name) {
        String valid = name == null ? "" : name.strip();
        if (valid.isBlank() || valid.length() > 36 || playlists.size() >= 50 || playlists.containsKey(valid)) return false;
        playlists.put(valid, new ArrayList<>());
        save(); return true;
    }
    public synchronized void add(String name, String trackId) {
        List<String> list = playlists.get(name);
        if (list != null && trackId != null && !list.contains(trackId) && list.size() < 1000) {
            list.add(trackId); save();
        }
    }
    public synchronized void remove(String name, String trackId) {
        List<String> list = playlists.get(name);
        if (list != null && list.remove(trackId)) save();
    }
    public synchronized void delete(String name) { if (playlists.remove(name) != null) save(); }

    private synchronized void read() {
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            int count = Math.clamp(Integer.parseInt(p.getProperty("count", "0")), 0, 50);
            for (int i = 0; i < count; i++) {
                String name = p.getProperty("playlist." + i + ".name", "").strip();
                if (name.isBlank() || name.length() > 36) continue;
                int size = Math.clamp(Integer.parseInt(p.getProperty("playlist." + i + ".size", "0")), 0, 1000);
                List<String> ids = new ArrayList<>();
                for (int j = 0; j < size; j++) {
                    String id = p.getProperty("playlist." + i + ".track." + j);
                    if (id != null && !id.isBlank()) ids.add(id);
                }
                playlists.put(name, ids);
            }
        } catch (IOException | NumberFormatException ignored) { }
    }
    private synchronized void save() {
        Properties p = new Properties();
        p.setProperty("count", Integer.toString(playlists.size()));
        int i = 0;
        for (Map.Entry<String, List<String>> entry : playlists.entrySet()) {
            String prefix = "playlist." + i++;
            p.setProperty(prefix + ".name", entry.getKey());
            p.setProperty(prefix + ".size", Integer.toString(entry.getValue().size()));
            for (int j = 0; j < entry.getValue().size(); j++)
                p.setProperty(prefix + ".track." + j, entry.getValue().get(j));
        }
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) { p.store(out, "AuraLink playlists"); }
        } catch (IOException ignored) { }
    }
}
