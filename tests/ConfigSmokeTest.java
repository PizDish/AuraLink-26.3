import dev.auralink.client.config.BridgePairing;
import dev.auralink.client.config.PlaylistStore;
import dev.auralink.client.config.PlayerPreferences;
import dev.auralink.client.config.MetadataStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConfigSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("auralink-test-");
        BridgePairing first=new BridgePairing(dir);
        BridgePairing second=new BridgePairing(dir);
        if(!first.key().equals(second.key())||!first.key().matches("[0-9a-f]{32}"))throw new AssertionError("Bad pairing key");
        PlaylistStore store=new PlaylistStore(dir);
        if(!store.create("Custom Test"))throw new AssertionError("Cannot make playlist");
        store.add("Custom Test","local:demo.wav");
        store.add("Custom Test","local:demo.wav");
        if(!store.tracks("Custom Test").equals(List.of("local:demo.wav")))throw new AssertionError("Duplicate track");
        if(!new PlaylistStore(dir).tracks("Custom Test").equals(List.of("local:demo.wav")))throw new AssertionError("Not persisted");
        PlayerPreferences prefs=new PlayerPreferences(dir);
        prefs.toggleWorldJoin();
        prefs.toggleDimensionChange();
        prefs.toggleFavorite("local:demo.wav");
        PlayerPreferences saved=new PlayerPreferences(dir);
        if(!saved.autoWorldJoin()||!saved.autoDimensionChange()||!saved.isFavorite("local:demo.wav"))throw new AssertionError("Settings not persisted");
        store.remove("Custom Test","local:demo.wav");
        if(!store.tracks("Custom Test").isEmpty())throw new AssertionError("Not removed");
        MetadataStore md=new MetadataStore(dir);
        md.set("local:demo.wav","New Name","Artist","Album");
        if(!new MetadataStore(dir).get("local:demo.wav").title().equals("New Name"))throw new AssertionError("Metadata not saved");
        System.out.println("PASS AuraLink pure Java config tests");
    }
}
