package dev.auralink.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.auralink.client.audio.PlayerController;
import dev.auralink.client.audio.TrackLibrary;
import dev.auralink.client.bridge.BridgeServer;
import dev.auralink.client.config.BridgePairing;
import dev.auralink.client.config.PlaylistStore;
import dev.auralink.client.config.PlayerPreferences;
import dev.auralink.client.events.WorldMusicEvents;
import dev.auralink.client.ui.AuraLinkScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Fully original client-only mod entrypoint. */
public final class AuraLinkClient implements ClientModInitializer {
    private static TrackLibrary library;
    private static PlayerPreferences preferences;
    private static PlayerController player;
    private static PlaylistStore playlists;
    private static BridgePairing pairing;
    private static BridgeServer bridge;
    private static WorldMusicEvents worldEvents;

    private static void initialize(Minecraft client) {
        if (library != null) return;
        var config = FabricLoader.getInstance().getConfigDir();
        library = new TrackLibrary(client.gameDirectory.toPath());
        preferences = new PlayerPreferences(config);
        playlists = new PlaylistStore(config);
        pairing = new BridgePairing(config);
        player = new PlayerController(client, preferences);
        worldEvents = new WorldMusicEvents(preferences, library, player);
        bridge = new BridgeServer(pairing.key());
        bridge.start();
        Runtime.getRuntime().addShutdownHook(new Thread(bridge::close, "AuraLink-bridge-cleanup"));
    }

    @Override public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("auralink", "player"));
        KeyMapping shortcut = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.auralink.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (library == null) initialize(client);
            while (shortcut.consumeClick()) open(client);
            if (player != null) player.tick();
            if (worldEvents != null) worldEvents.tick(client);
        });
    }

    public static void open(Minecraft client) {
        initialize(client);
        library.refresh();
        client.gui.setScreen(new AuraLinkScreen(library, player, preferences, playlists, bridge, pairing));
    }
}
