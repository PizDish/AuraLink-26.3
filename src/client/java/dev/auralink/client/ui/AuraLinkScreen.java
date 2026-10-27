package dev.auralink.client.ui;

import dev.auralink.client.audio.PlayerController;
import dev.auralink.client.audio.Track;
import dev.auralink.client.audio.TrackLibrary;
import dev.auralink.client.bridge.BridgeServer;
import dev.auralink.client.config.BridgePairing;
import dev.auralink.client.config.PlaylistStore;
import dev.auralink.client.config.PlayerPreferences;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tabbed interface: local music, favorites, editable playlists, and Yandex browser controls. */
public final class AuraLinkScreen extends Screen {
    private enum Filter { ALL, DISCS, LOCAL, FAVORITES, QUEUE, PLAYLISTS, META, YANDEX }
    private final TrackLibrary library;
    private final PlayerController player;
    private final PlayerPreferences settings;
    private final PlaylistStore playlists;
    private final BridgeServer bridge;
    private final BridgePairing pairing;
    private Filter filter = Filter.ALL;
    private int page;
    private int visibleRows;
    private int left;
    private int top;
    private int panelWidth;
    private String selectedPlaylist = "My Mix";
    private String notice = "";
    private EditBox playlistName;
    private EditBox metaTitle;
    private EditBox metaArtist;
    private EditBox metaAlbum;
    private String metaId = "";

    public AuraLinkScreen(TrackLibrary library, PlayerController player, PlayerPreferences settings,
                          PlaylistStore playlists, BridgeServer bridge, BridgePairing pairing) {
        super(Component.literal("AuraLink"));
        this.library = library;
        this.player = player;
        this.settings = settings;
        this.playlists = playlists;
        this.bridge = bridge;
        this.pairing = pairing;
    }
    private Button button(String label, int x, int y, int width, Runnable command) {
        Button widget = Button.builder(Component.literal(label), b -> command.run()).bounds(x,y,width,20).build();
        return addRenderableWidget(widget);
    }
    @Override protected void init() {
        panelWidth = Math.min(432, width - 18);
        left = (width - panelWidth) / 2;
        top = 9;
        visibleRows = Math.clamp((height - 160) / 23, 2, 8);
        int inner = panelWidth - 12;
        String[] labels = {"ALL", "DISCS", "WAV", "LIKED", "QUEUE", "LISTS", "META", "YANDEX"};
        Filter[] values = Filter.values();
        int tw = Math.max(31, (inner - 21) / 8);
        for (int i=0;i<values.length;i++) {
            final Filter value = values[i];
            button(filter==value ? "["+labels[i]+"]" : labels[i],left+6+i*(tw+3),top+28,tw,()->switchFilter(value));
        }
        if (filter == Filter.YANDEX) { initYandex(inner); return; }
        if (filter == Filter.QUEUE) { initQueue(inner); return; }
        if (filter == Filter.META) { initMetadata(inner); return; }
        if (filter == Filter.PLAYLISTS) { initPlaylists(inner); return; }
        renderLocal(inner);
    }

    private void renderLocal(int inner) {
        List<Track> list = matchingTracks();
        int pages = Math.max(1,(list.size()+visibleRows-1)/visibleRows);
        page = Math.clamp(page,0,pages-1);
        int topOfList = top+56;
        for (int i=0;i<visibleRows;i++) {
            int idx = page*visibleRows+i;
            if (idx>=list.size()) break;
            Track track = list.get(idx);
            int y = topOfList+i*23;
            String prefix = track.kind()==Track.Kind.LOCAL ? "♪ " : track.kind()==Track.Kind.DISC ? "♫ " : "· ";
            button(prefix+shorten(track.title(),inner-94),left+6,y,inner-80,()->player.play(track,list));
            button(settings.isFavorite(track.id())?"★":"☆",left+inner-70,y,22,()->{
                settings.toggleFavorite(track.id());rebuild();
            });
            button("+",left+inner-46,y,22,()->{
                playlists.add(selectedPlaylist,track.id());notice="Added to "+selectedPlaylist;rebuild();
            });
            button("Q",left+inner-22,y,22,()->{player.enqueue(track);notice="Added to queue";rebuild();});
        }
        int c = topOfList+visibleRows*23+4;
        button("< PAGE",left+6,c,72,()->shiftPage(-1));
        button("|<",left+82,c,42,player::previous);
        button("PLAY/PAUSE",left+128,c,Math.max(70,inner-264),player::togglePause);
        button(">|",left+inner-129,c,42,player::next);
        button("PAGE >",left+inner-82,c,82,()->shiftPage(1));
        int opt = c+24;
        button(settings.isShuffle()?"SHUFFLE ON":"SHUFFLE OFF",left+6,opt,108,()->{
            settings.toggleShuffle();rebuild();
        });
        String repeat = switch(settings.repeatMode()) {case 1->"REPEAT ALL";case 2->"REPEAT ONE";default->"REPEAT OFF";};
        button(repeat,left+118,opt,104,()->{settings.cycleRepeat();rebuild();});
        button("STOP",left+226,opt,54,player::stop);
        button("SCAN",left+284,opt,Math.max(40,inner-284),()->{library.refresh();rebuild();});
        int vol = opt+24;
        button("VOL -",left+6,vol,65,()->{settings.changeVolume(-10);player.applyVolume();rebuild();});
        button("VOL +",left+75,vol,65,()->{settings.changeVolume(10);player.applyVolume();rebuild();});
        button("-10s",left+150,vol,65,()->player.seek(-10));
        button("+10s",left+219,vol,65,()->player.seek(10));
        button("JOIN:"+(settings.autoWorldJoin()?"ON":"OFF"),left+inner-100,vol,100,()->{
            settings.toggleWorldJoin();rebuild();
        });
    }



    private void initMetadata(int inner) {
        List<Track> all=library.all();
        int perPage=4;
        page=Math.clamp(page,0,Math.max(0,(all.size()-1)/perPage));
        int y=top+58;
        for(int i=0;i<perPage;i++) {
            int index=page*perPage+i;
            if(index>=all.size())break;
            Track track=all.get(index);
            int row=y+i*23;
            button((track.id().equals(metaId)?"[EDIT] ":"")+shorten(track.title(),inner-25),left+6,row,inner,
                ()->{metaId=track.id();rebuild();});
        }
        y+=perPage*23+3;
        button("PREV",left+6,y,66,()->{page=Math.max(0,page-1);rebuild();});
        button("NEXT",left+76,y,66,()->{page++;rebuild();});
        y+=26;
        Track selected=all.stream().filter(t->t.id().equals(metaId)).findFirst().orElse(null);
        if (selected==null) return;
        var override=library.metadata().get(selected.id());
        metaTitle=new EditBox(font,left+6,y,inner,20,Component.literal("Title"));
        metaTitle.setHint(Component.literal("Title"));metaTitle.setMaxLength(120);
        metaTitle.setValue(override==null?selected.title():override.title());addRenderableWidget(metaTitle);
        metaArtist=new EditBox(font,left+6,y+24,inner,20,Component.literal("Artist"));
        metaArtist.setHint(Component.literal("Artist"));metaArtist.setMaxLength(120);
        metaArtist.setValue(override==null?"":override.artist());addRenderableWidget(metaArtist);
        metaAlbum=new EditBox(font,left+6,y+48,inner,20,Component.literal("Album"));
        metaAlbum.setHint(Component.literal("Album"));metaAlbum.setMaxLength(120);
        metaAlbum.setValue(override==null?"":override.album());addRenderableWidget(metaAlbum);
        button("SAVE METADATA",left+6,y+74,inner-106,()->{
            library.metadata().set(metaId,metaTitle.getValue(),metaArtist.getValue(),metaAlbum.getValue());
            library.refresh();notice="Metadata saved";rebuild();
        });
        button("RESET",left+inner-100,y+74,100,()->{
            library.metadata().remove(metaId);library.refresh();notice="Metadata cleared";rebuild();
        });
    }

    private void initQueue(int inner) {
        List<Track> list = player.queue();
        int y=top+60;
        int rows=Math.clamp((height-y-50)/23,2,10);
        page=Math.clamp(page,0,Math.max(0,(list.size()-1)/rows));
        for(int i=0;i<rows;i++) {
            int index=page*rows+i;
            if(index>=list.size()) break;
            Track track=list.get(index);
            int row=y+i*23;
            button((index==player.queueIndex()?"▶ ":"  ")+shorten(track.title(),inner-140),left+6,row,inner-120,
                ()->player.play(track,player.queue()));
            button("↑",left+inner-110,row,34,()->{player.moveQueued(index,-1);rebuild();});
            button("↓",left+inner-74,row,34,()->{player.moveQueued(index,1);rebuild();});
            button("X",left+inner-38,row,38,()->{player.removeQueued(index);rebuild();});
        }
        int controls=top+60+rows*23+5;
        button("< PAGE",left+6,controls,70,()->{page=Math.max(0,page-1);rebuild();});
        button("PAGE >",left+81,controls,70,()->{page++;rebuild();});
        button("CLEAR",left+inner-85,controls,85,()->{player.clearQueue();rebuild();});
    }

    private void initPlaylists(int inner) {
        int y=top+57;
        int i=0;
        for(String name:playlists.names()) {
            if (i++>=5) break;
            final String thisName=name;
            button(name.equals(selectedPlaylist)?"["+shorten(name,inner-95)+"]":shorten(name,inner-95),
                left+6,y,inner-115,()->{selectedPlaylist=thisName;page=0;rebuild();});
            button("PLAY",left+inner-104,y,50,()->playList(thisName));
            button("DEL",left+inner-50,y,50,()->{
                playlists.delete(thisName);
                selectedPlaylist=playlists.names().stream().findFirst().orElse("My Mix");rebuild();
            });
            y+=23;
        }
        y=Math.max(y,top+172);
        playlistName=new EditBox(font,left+6,y,inner-98,20,Component.literal("Playlist name"));
        playlistName.setMaxLength(36);
        playlistName.setHint(Component.literal("New playlist..."));
        addRenderableWidget(playlistName);
        button("CREATE",left+inner-87,y,87,()->{
            if (playlists.create(playlistName.getValue())) {
                selectedPlaylist=playlistName.getValue().strip();notice="Playlist created";
            } else notice="Choose a new name (max 36)";
            rebuild();
        });
        y+=28;
        List<Track> choices = selectionForPlaylist();
        int listRows = Math.clamp((height-y-48)/23,1,6);
        for(int j=0;j<listRows && j<choices.size();j++) {
            final Track item=choices.get(j);
            int rowY=y+j*23;
            button(shorten(item.title(),inner-70),left+6,rowY,inner-55,()->player.play(item,choices));
            button("-",left+inner-46,rowY,46,()->{
                playlists.remove(selectedPlaylist,item.id());rebuild();
            });
        }
    }
    private void playList(String name) {
        selectedPlaylist=name;
        List<Track> list=selectionForPlaylist();
        if (!list.isEmpty()) player.play(list.get(0),list);
        else notice="Playlist is empty: use + next to tracks";
    }
    private List<Track> selectionForPlaylist() {
        var ids=playlists.tracks(selectedPlaylist);
        return ids.stream().map(id->library.all().stream().filter(t->t.id().equals(id)).findFirst().orElse(null))
            .filter(java.util.Objects::nonNull).toList();
    }

    private void initYandex(int inner) {
        int y=top+65;
        button("OPEN YANDEX MUSIC",left+6,y,inner,()->{
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create("https://music.yandex.ru"));
                else notice="Open https://music.yandex.ru in your browser";
            } catch(Exception ex) { notice="Open https://music.yandex.ru in your browser"; }
        });
        y+=50;
        button("|<",left+6,y,62,()->bridge.send("previous"));
        button("PLAY / PAUSE",left+74,y,Math.max(75,inner-222),()->bridge.send("play_pause"));
        button(">|",left+inner-140,y,62,()->bridge.send("next"));
        button("LIKE",left+inner-72,y,72,()->bridge.send("like"));
        y+=24;
        button("SHUFFLE",left+6,y,inner/2-4,()->bridge.send("shuffle"));
        button("REPEAT",left+inner/2+4,y,inner/2-4,()->bridge.send("repeat"));
        y+=35;
        button("COPY PAIRING KEY",left+6,y,inner,()->{
            // Clipboard is never sent over network; only the user clicks this control.
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(pairing.key()), null);
            notice="Pairing key copied; paste into AuraLink browser extension";rebuild();
        });
    }

    private List<Track> matchingTracks() {
        return library.all().stream().filter(t->switch(filter) {
            case ALL -> true;
            case DISCS -> t.kind()==Track.Kind.DISC;
            case LOCAL -> t.kind()==Track.Kind.LOCAL;
            case FAVORITES -> settings.isFavorite(t.id());
            default -> false;
        }).toList();
    }
    private String shorten(String raw,int maxPixels) {
        if(font.width(raw)<=maxPixels)return raw;
        while(!raw.isEmpty()&&font.width(raw+"…")>maxPixels)raw=raw.substring(0,raw.length()-1);
        return raw+"…";
    }
    private void switchFilter(Filter next) {filter=next;page=0;rebuild();}
    private void shiftPage(int direction) {
        int pages=Math.max(1,(matchingTracks().size()+visibleRows-1)/visibleRows);
        page=Math.floorMod(page+direction,pages);rebuild();
    }
    private void rebuild() {
        AuraLinkScreen fresh=new AuraLinkScreen(library,player,settings,playlists,bridge,pairing);
        fresh.filter=filter;fresh.page=page;fresh.selectedPlaylist=selectedPlaylist;fresh.notice=notice;fresh.metaId=metaId;
        this.minecraft.gui.setScreen(fresh);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xE8121824);
        g.fill(left-2,top-2,left+panelWidth+2,height-4,0xF0182434);
        g.fill(left-2,top-2,left+panelWidth+2,top+1,0xFF3CB8D6);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractRenderState(g,mx,my,delta);
        g.centeredText(font,Component.literal("AURALINK  //  MINECRAFT 26.3"),width/2,top+6,0xFF72E5ED);
        g.centeredText(font,Component.literal("Local music + Yandex browser companion"),width/2,top+18,0xFF98BACC);
        if(filter==Filter.QUEUE) {
            g.centeredText(font,Component.literal("QUEUE: "+player.queue().size()+" songs (drag-free ↑ ↓)"),width/2,top+43,0xFFB3C7DD);
        } else if(filter==Filter.META) {
            g.centeredText(font,Component.literal("EDIT TITLE / ARTIST / ALBUM"),width/2,top+44,0xFFB3C7DD);
        } else if(filter==Filter.YANDEX) {
            var st=bridge.state();
            String conn=bridge.connected()?"CONNECTED: YANDEX PLAYER":"NOT CONNECTED: OPEN EXTENSION";
            g.centeredText(font,Component.literal(conn),width/2,top+100,bridge.connected()?0xFF7DF2BF:0xFFFFB567);
            String title=st.title().isBlank()?"No song details yet":st.artist()+" - "+st.title();
            g.centeredText(font,Component.literal(shorten(title,panelWidth-15)),width/2,top+190,0xFFF7EAC0);
            g.centeredText(font,Component.literal("Plays in your Yandex Music browser tab"),width/2,top+203,0xFFB3C7DD);
            if(!bridge.startupError().isBlank())g.centeredText(font,Component.literal(shorten(bridge.startupError(),panelWidth-16)),width/2,top+218,0xFFFF8181);
        } else if(filter==Filter.PLAYLISTS) {
            g.centeredText(font,Component.literal("PLAYLIST: "+selectedPlaylist),width/2,top+43,0xFFE5E9BB);
            g.centeredText(font,Component.literal("Use + next to a song to add to selected playlist"),width/2,height-18,0xFFA6B9C9);
        } else {
            var track=player.current();
            String state=track==null?"No track selected":(player.isPaused()?"PAUSED: ":"PLAYING: ")+track.title();
            g.centeredText(font,Component.literal(shorten(state,panelWidth-20)),width/2,
                top+56+visibleRows*23+78,0xFFF9E9B5);
            g.centeredText(font,Component.literal(matchingTracks().size()+" tracks / page "+(page+1)),width/2,top+46,0xFF9DB6CB);
        }
        if(!notice.isBlank())g.centeredText(font,Component.literal(shorten(notice,panelWidth-20)),width/2,height-18,0xFFFFD187);
    }
    @Override public boolean isPauseScreen() { return false; }
}
