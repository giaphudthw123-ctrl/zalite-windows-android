package org.ctdozlite.mobile;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Worker-only bounded content-addressed files. Callers also validate the GIF codec through Rust/Android. */
final class GifFiles {
    static final int MAX_FILE_BYTES=8*1024*1024;
    private static final Object IO_LOCK=new Object();
    private final File directory;
    private final int maxItems;
    private final long maxBytes;
    GifFiles(File directory){this(directory,100,256L*1024*1024);}
    GifFiles(File directory,int maxItems,long maxBytes){this.directory=directory;this.maxItems=maxItems;this.maxBytes=maxBytes;}
    private File file(String id) throws IOException {
        if(id==null||!id.matches("[0-9a-f]{64}"))throw new IOException("Invalid GIF identity");
        return new File(directory,id+".gif");
    }
    static String identity(byte[] bytes) throws Exception {
        StringBuilder value=new StringBuilder();for(byte item:MessageDigest.getInstance("SHA-256").digest(bytes))value.append(String.format(Locale.ROOT,"%02x",item&255));return value.toString();
    }
    private File[] entries() throws IOException {
        if(!directory.exists())return new File[0];
        if(Files.isSymbolicLink(directory.toPath())||!directory.isDirectory())throw new IOException("Invalid GIF directory");
        File[] files=directory.listFiles();if(files==null)throw new IOException("Cannot list GIF directory");return files;
    }
    List<String> ids() throws IOException {
        synchronized(IO_LOCK){List<String> result=new ArrayList<>();for(File entry:entries())if(entry.getName().matches("[0-9a-f]{64}\\.gif")){
            if(!Files.isRegularFile(entry.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid GIF entry");
            result.add(entry.getName().substring(0,64));
        }if(result.size()>maxItems)throw new IOException("GIF library over item limit");Collections.sort(result);return result;}
    }
    byte[] read(String id) throws Exception {synchronized(IO_LOCK){return readLocked(id);}}
    private byte[] readLocked(String id) throws Exception {
        entries();File source=file(id);if(!Files.isRegularFile(source.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("GIF missing or unsafe");
        byte[] bytes;
        try(InputStream input=new FileInputStream(source);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(output.size()+count>MAX_FILE_BYTES)throw new IOException("GIF too large");output.write(buffer,0,count);}bytes=output.toByteArray();
        }
        if(bytes.length==0||!identity(bytes).equals(id))throw new IOException("GIF identity mismatch");return bytes;
    }
    String put(byte[] bytes) throws Exception {
        if(bytes==null||bytes.length==0||bytes.length>MAX_FILE_BYTES)throw new IOException("Invalid GIF size");
        String id=identity(bytes);
        synchronized(IO_LOCK) {
            if(!directory.exists()&&!directory.mkdirs())throw new IOException("Cannot create GIF directory");
            File target=file(id);File[] files=entries();
            if(Files.exists(target.toPath(),LinkOption.NOFOLLOW_LINKS)){readLocked(id);return id;}
            long total=0;int items=0;
            for(File item:files){if(!Files.isRegularFile(item.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe GIF library entry");total=Math.addExact(total,item.length());if(item.getName().endsWith(".gif"))items++;}
            if(items>=maxItems||total>maxBytes-bytes.length)throw new IOException("GIF library quota exceeded");
            File temporary=File.createTempFile("gif-import-",".tmp",directory);
            try {
                try(FileOutputStream output=new FileOutputStream(temporary)){output.write(bytes);output.getFD().sync();}
                Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);
                readLocked(id);return id;
            }finally{Files.deleteIfExists(temporary.toPath());}
        }
    }
    void remove(String id) throws IOException {synchronized(IO_LOCK){entries();Files.deleteIfExists(file(id).toPath());}}
}
