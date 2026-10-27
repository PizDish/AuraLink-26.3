package dev.auralink.client.audio;

import dev.auralink.client.config.PlayerPreferences;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Lightweight playback engine: vanilla SoundEvents or PCM WAV using Java Sound. */
public final class PlayerController {
    private final Minecraft client;
    private final PlayerPreferences preferences;
    private final Random random = new Random();
    private List<Track> queue = new ArrayList<>();
    private Track current;
    private SimpleSoundInstance sound;
    private Clip clip;
    private int index = -1;
    private int ticks;
    private boolean paused;
    private String error = "";

    public PlayerController(Minecraft client, PlayerPreferences preferences) {
        this.client = client;
        this.preferences = preferences;
    }

    public Track current() { return current; }

    public List<Track> queue() { return List.copyOf(queue); }
    public int queueIndex() { return index; }
    public void enqueue(Track track) {
        if (track == null || queue.size() >= 1000) return;
        queue.add(track);
        if (current == null) startIndex(queue.size() - 1);
    }
    public void moveQueued(int at, int direction) {
        int target = at + direction;
        if (at < 0 || at >= queue.size() || target < 0 || target >= queue.size()) return;
        Track moving = queue.remove(at);
        queue.add(target, moving);
        if (index == at) index = target;
        else if (at < index && target >= index) index--;
        else if (at > index && target <= index) index++;
    }
    public void removeQueued(int at) {
        if (at < 0 || at >= queue.size()) return;
        boolean currentRemoved = at == index;
        queue.remove(at);
        if (queue.isEmpty()) { stop(); return; }
        if (currentRemoved) { closeAudio(); startIndex(Math.min(at,queue.size()-1)); }
        else if (at < index) index--;
    }
    public void clearQueue() { stop(); queue.clear(); }

    public boolean isPaused() { return paused; }
    public String error() { return error; }
    public boolean playing() { return current != null && !paused; }
    public int positionSeconds() { return clip == null ? 0 : (int) (clip.getMicrosecondPosition() / 1_000_000L); }
    public int lengthSeconds() { return clip == null ? 0 : (int) (clip.getMicrosecondLength() / 1_000_000L); }

    public void play(Track requested, List<Track> selection) {
        queue = new ArrayList<>(selection);
        int position = queue.indexOf(requested);
        if (position < 0) return;
        startIndex(position);
    }

    private void startIndex(int nextIndex) {
        closeAudio();
        error = "";
        index = nextIndex;
        current = queue.get(index);
        paused = false;
        ticks = 0;
        if (current.isLocal()) startWav();
        else {
            sound = SimpleSoundInstance.forMusic(current.soundEvent());
            client.getSoundManager().play(sound);
        }
    }

    private void startWav() {
        try {
            if (Files.size(current.localFile()) > 60L * 1024 * 1024)
                throw new IOException("WAV file exceeds 60 MB safety limit");
            try (AudioInputStream source = AudioSystem.getAudioInputStream(current.localFile().toFile())) {
                AudioFormat format = source.getFormat();
                AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(), 16, format.getChannels(), format.getChannels() * 2,
                    format.getSampleRate(), false);
                try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, source)) {
                    clip = AudioSystem.getClip();
                    clip.open(converted);
                    applyVolume();
                    clip.start();
                }
            }
        } catch (Exception ex) {
            error = "Unable to play WAV: " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
            if (clip != null) { clip.close(); clip = null; }
            current = null;
            index = -1;
        }
    }

    public void applyVolume() {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        int volume = preferences.volume();
        float db = volume == 0 ? gain.getMinimum() : (float) (20.0 * Math.log10(volume / 100.0));
        gain.setValue(Math.clamp(db, gain.getMinimum(), gain.getMaximum()));
    }

    public void togglePause() {
        if (current == null) return;
        if (clip != null) {
            if (paused) clip.start(); else clip.stop();
        } else if (sound != null) {
            // Vanilla's per-instance SoundEvent lacks a stable position-based pause API.
            // Resuming vanilla soundtrack restarts the current event.
            if (!paused) client.getSoundManager().stop(sound);
            else {
                sound = SimpleSoundInstance.forMusic(current.soundEvent());
                client.getSoundManager().play(sound);
                ticks = 0;
            }
        }
        paused = !paused;
    }

    public void seek(int seconds) {
        if (clip == null) return;
        long target = Math.clamp(clip.getMicrosecondPosition() + seconds * 1_000_000L,
            0, clip.getMicrosecondLength());
        clip.setMicrosecondPosition(target);
    }

    public void stop() {
        closeAudio();
        current = null;
        index = -1;
        paused = false;
        ticks = 0;
    }

    private void closeAudio() {
        if (sound != null) {
            client.getSoundManager().stop(sound);
            sound = null;
        }
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    public void next() { advance(1, false); }
    public void previous() { advance(-1, false); }

    private void advance(int direction, boolean auto) {
        if (queue.isEmpty()) { stop(); return; }
        if (auto && preferences.repeatMode() == 2 && index >= 0) {
            startIndex(index);
            return;
        }
        if (preferences.isShuffle() && queue.size() > 1) {
            int choice;
            do { choice = random.nextInt(queue.size()); } while (choice == index);
            startIndex(choice);
            return;
        }
        int choice = index + direction;
        if (choice < 0 || choice >= queue.size()) {
            if (auto && preferences.repeatMode() == 0) { stop(); return; }
            choice = Math.floorMod(choice, queue.size());
        }
        startIndex(choice);
    }

    public void tick() {
        if (current == null || paused) return;
        // Initial delay avoids advancing while the asynchronous sound engine starts.
        if (++ticks < 60) return;
        if (clip != null) {
            if (!clip.isRunning() && clip.getMicrosecondPosition() >= clip.getMicrosecondLength() - 20_000L)
                advance(1, true);
        } else if (sound != null && !client.getSoundManager().isActive(sound)) {
            advance(1, true);
        }
    }
}
