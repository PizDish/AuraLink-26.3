package dev.auralink.client.audio;

import java.io.IOException;
import dev.auralink.client.config.MetadataStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Merges vanilla music SoundEvents with user-owned PCM WAV files. */
public final class TrackLibrary {
    private final Path musicDirectory;
    private List<Track> tracks = List.of();
    private final MetadataStore metadata;
    private String lastError = "";

    public TrackLibrary(Path gameDirectory) {
        this.musicDirectory = gameDirectory.resolve("auralink").resolve("tracks");
        metadata = new MetadataStore(gameDirectory.resolve("config"));
    }

    public Path musicDirectory() { return musicDirectory; }
    public MetadataStore metadata() { return metadata; }
    public List<Track> all() { return tracks; }
    public String lastError() { return lastError; }

    public void refresh() {
        List<Track> found = new ArrayList<>();
        lastError = "";
        for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            Track.Kind kind;
            if (path.startsWith("music_disc.") || path.startsWith("record.")) {
                kind = Track.Kind.DISC;
            } else if (path.startsWith("music.") || path.equals("music")) {
                kind = Track.Kind.SOUNDTRACK;
            } else {
                continue;
            }
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
            if (sound == null) continue;
            String shortName = path.substring(path.lastIndexOf('.') + 1);
            found.add(Track.game(id.toString(), formatTitle(shortName), kind, sound));
        }
        try {
            Files.createDirectories(musicDirectory);
            try (Stream<Path> files = Files.list(musicDirectory)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"))
                    .limit(1000)
                    .forEach(p -> found.add(Track.local(p.getFileName().toString(), p)));
            }
        } catch (IOException e) {
            lastError = "Cannot read local tracks: " + e.getMessage();
        }
        var decorated = new ArrayList<Track>();
        for (Track track : found) {
            var override=metadata.get(track.id());
            if (override==null) decorated.add(track);
            else {
                String title=override.title().isBlank()?track.title():override.title();
                if(!override.artist().isBlank())title=override.artist()+" - "+title;
                decorated.add(new Track(track.id(),title,track.kind(),track.soundEvent(),track.localFile()));
            }
        }
        decorated.sort(Comparator.comparing(Track::kind).thenComparing(Track::title, String.CASE_INSENSITIVE_ORDER));
        tracks = List.copyOf(decorated);
    }

    private static String formatTitle(String raw) {
        String[] words = raw.replace('_', ' ').replace('-', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.toString();
    }
}
