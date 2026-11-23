package org.ctdozlite.mobile;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;

/** Worker-only, versioned local preference; invalid existing files must not disable protection. */
final class CaptureSettings {
    private static final Object IO_LOCK=new Object();
    private final AtomicFile file;
    CaptureSettings(Context context) {
        File directory=context.getNoBackupFilesDir();
        if(directory==null)throw new IllegalStateException("Private storage unavailable");
        file=new AtomicFile(new File(directory,"capture-policy.bin"));
    }
    static boolean decode(byte[] bytes) throws IOException {
        if(bytes.length!=4||bytes[0]!='Z'||bytes[1]!='C'||bytes[2]!=1||(bytes[3]!=0&&bytes[3]!=1))throw new IOException("Invalid capture policy");
        return bytes[3]==1;
    }
    boolean read() throws IOException {synchronized(IO_LOCK){return readLocked();}}
    private boolean readLocked() throws IOException {
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists())return false;
        try(InputStream input=file.openRead();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            int value;while((value=input.read())!=-1){if(bytes.size()>=4)throw new IOException("Capture policy too large");bytes.write(value);}
            return decode(bytes.toByteArray());
        }
    }
    void save(boolean enabled) throws IOException {
        synchronized(IO_LOCK) {
            FileOutputStream output=null;
            try{output=file.startWrite();output.write(new byte[]{'Z','C',1,(byte)(enabled?1:0)});file.finishWrite(output);}
            catch(IOException error){if(output!=null)file.failWrite(output);throw error;}
            if(readLocked()!=enabled)throw new IOException("Capture policy verification failed");
        }
    }
}
