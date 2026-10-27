package dev.auralink.client.events;

import dev.auralink.client.audio.PlayerController;
import dev.auralink.client.audio.Track;
import dev.auralink.client.audio.TrackLibrary;
import dev.auralink.client.config.PlayerPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Opt-in soundtrack on world join or dimension change. */
public final class WorldMusicEvents {
    private ResourceKey<Level> lastDimension;
    private boolean wasInWorld;
    private final PlayerPreferences settings;
    private final TrackLibrary library;
    private final PlayerController player;

    public WorldMusicEvents(PlayerPreferences settings, TrackLibrary library, PlayerController player) {
        this.settings = settings; this.library = library; this.player = player;
    }
    public void tick(Minecraft client) {
        if (client.level == null) {
            wasInWorld = false;
            lastDimension = null;
            return;
        }
        ResourceKey<Level> dimension = client.level.dimension();
        boolean joined = !wasInWorld;
        boolean changed = wasInWorld && !dimension.equals(lastDimension);
        wasInWorld = true;
        lastDimension = dimension;
        if ((joined && settings.autoWorldJoin()) || (changed && settings.autoDimensionChange())) {
            library.refresh();
            var choices = library.all().stream().filter(t -> t.kind() == Track.Kind.SOUNDTRACK).toList();
            if (!choices.isEmpty()) player.play(choices.get(0), choices);
        }
    }
}
