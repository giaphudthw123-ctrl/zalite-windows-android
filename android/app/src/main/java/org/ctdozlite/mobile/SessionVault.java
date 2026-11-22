package org.ctdozlite.mobile;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** User Session bytes are written only as authenticated ciphertext; never as a backup JSON. */
final class SessionVault {
    private static final int LIMIT=8*1024*1024;
    private final AtomicFile file;
    private final String alias;
    SessionVault(Context context) { this(context,"zalite.session.v1","session-vault.bin"); }
    private SessionVault(Context context,String alias,String name) { this.alias=alias;file=new AtomicFile(new File(context.getNoBackupFilesDir(),name)); }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(alias)) return (SecretKey)store.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    synchronized void save(byte[] bytes) throws Exception {
        if(bytes==null||bytes.length==0||bytes.length>LIMIT-4096) throw new IOException("Invalid Session size");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] sealed=cipher.doFinal(bytes), iv=cipher.getIV();
        if(iv.length!=12) throw new IOException("Invalid IV");
        FileOutputStream output=null;
        try { output=file.startWrite();output.write(new byte[]{'Z','S','V',1});output.write(iv);output.write(sealed);file.finishWrite(output); }
        catch(Exception error) { if(output!=null) file.failWrite(output);throw error; }
    }
    synchronized byte[] read() throws Exception {
        byte[] bytes;
        try(InputStream input=file.openRead();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1){if(output.size()+count>LIMIT)throw new IOException("Vault too large");output.write(buffer,0,count);}bytes=output.toByteArray();
        }
        if(bytes.length<32||bytes[0]!='Z'||bytes[1]!='S'||bytes[2]!='V'||bytes[3]!=1)throw new IOException("Invalid vault");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(bytes,4,16)));
        return cipher.doFinal(bytes,16,bytes.length-16);
    }
    static void selfTest(Context context) throws Exception {
        SessionVault vault=new SessionVault(context,"zalite.session.selftest","session-vault-selftest.bin");
        byte[] fixture="synthetic-session-only".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            vault.save(fixture);if(!Arrays.equals(vault.read(),fixture))throw new IOException("Roundtrip failed");
            byte[] first=java.nio.file.Files.readAllBytes(vault.file.getBaseFile().toPath());
            vault.save(fixture);byte[] second=java.nio.file.Files.readAllBytes(vault.file.getBaseFile().toPath());
            if(Arrays.equals(first,second))throw new IOException("Repeated IV/ciphertext");
            second[second.length-1]^=1;java.nio.file.Files.write(vault.file.getBaseFile().toPath(),second);
            boolean rejected=false;try{vault.read();}catch(Exception expected){rejected=true;}
            if(!rejected)throw new IOException("Tampering accepted");
        } finally { vault.file.delete();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(vault.alias);Arrays.fill(fixture,(byte)0); }
    }
}
