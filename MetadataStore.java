package dev.auralink.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Optional custom titles, artists and album labels; local files are not modified. */
public final class MetadataStore {
    public record Metadata(String title, String artist, String album) { }
    private final Path file;
    private final Map<String, Metadata> overrides = new LinkedHashMap<>();
    public MetadataStore(Path configDir) {file=configDir.resolve("auralink-metadata.properties");load();}
    public synchronized Metadata get(String trackId) { return overrides.get(trackId); }
    public synchronized void set(String id,String title,String artist,String album) {
        if (id==null || id.isBlank()) return;
        overrides.put(id,new Metadata(limit(title),limit(artist),limit(album)));
        save();
    }
    public synchronized void remove(String id) {if(overrides.remove(id)!=null)save();}
    private static String limit(String value) {return value==null?"":value.strip().substring(0,Math.min(value.strip().length(),120));}
    private synchronized void load() {
        if(!Files.isRegularFile(file))return;
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(file)){
            p.load(in);
            int size=Math.clamp(Integer.parseInt(p.getProperty("size","0")),0,5000);
            for(int i=0;i<size;i++){
                String key=p.getProperty(i+".id", "");
                if(!key.isBlank())overrides.put(key,new Metadata(p.getProperty(i+".title",""),
                    p.getProperty(i+".artist",""),p.getProperty(i+".album","")));
            }
        }catch(IOException|NumberFormatException ignored){}
    }
    private synchronized void save(){
        Properties p=new Properties();p.setProperty("size",Integer.toString(overrides.size()));
        int i=0;
        for(var e:overrides.entrySet()){
            String prefix=String.valueOf(i++);
            p.setProperty(prefix+".id",e.getKey());
            p.setProperty(prefix+".title",e.getValue().title());
            p.setProperty(prefix+".artist",e.getValue().artist());
            p.setProperty(prefix+".album",e.getValue().album());
        }
        try{Files.createDirectories(file.getParent());try(OutputStream out=Files.newOutputStream(file)){
            p.store(out,"AuraLink optional track metadata");
        }}catch(IOException ignored){}
    }
}
