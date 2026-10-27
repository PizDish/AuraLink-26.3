package dev.auralink.client.audio;

import java.nio.file.Path;
import net.minecraft.sounds.SoundEvent;

/** Exactly one of soundEvent and localFile is non-null. */
public record Track(String id, String title, Kind kind, SoundEvent soundEvent, Path localFile) {
    public enum Kind { SOUNDTRACK, DISC, LOCAL }

    public static Track game(String id, String title, Kind kind, SoundEvent sound) {
        return new Track(id, title, kind, sound, null);
    }

    public static Track local(String filename, Path file) {
        String title = filename.substring(0, filename.length() - 4).replace('_', ' ');
        return new Track("local:" + filename, title, Kind.LOCAL, null, file);
    }

    public boolean isLocal() { return kind == Kind.LOCAL; }
}
