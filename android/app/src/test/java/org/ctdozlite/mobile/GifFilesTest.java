package org.ctdozlite.mobile;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public class GifFilesTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void deduplicatesWithoutChargingQuotaTwice() throws Exception {
        File directory=temporary.newFolder();GifFiles files=new GifFiles(directory,1,3);
        String id=files.put(new byte[]{1,2,3});assertEquals(id,files.put(new byte[]{1,2,3}));assertEquals(Collections.singletonList(id),files.ids());assertArrayEquals(new byte[]{1,2,3},files.read(id));assertEquals(1,directory.list().length);
    }
    @Test public void rejectsTraversalAndNoncanonicalIdentity() throws Exception {
        GifFiles files=new GifFiles(temporary.newFolder());
        for(String id:new String[]{"../outside","a.gif","A".repeat(64),"a".repeat(63),""}){try{files.read(id);fail(id);}catch(IOException expected){}}
    }
    @Test public void detectsTamperAndDoesNotOverwriteOnReimport() throws Exception {
        File directory=temporary.newFolder();GifFiles files=new GifFiles(directory);String id=files.put(new byte[]{1,2,3});File target=new File(directory,id+".gif");Files.write(target.toPath(),new byte[]{9});
        try{files.read(id);fail();}catch(IOException expected){}
        try{files.put(new byte[]{1,2,3});fail();}catch(IOException expected){}
        assertArrayEquals(new byte[]{9},Files.readAllBytes(target.toPath()));
    }
    @Test public void quotaFailurePreservesOriginalAndLeavesNoTemporaryFile() throws Exception {
        File directory=temporary.newFolder();GifFiles files=new GifFiles(directory,3,4);String id=files.put(new byte[]{1,2,3});
        try{files.put(new byte[]{4,5});fail();}catch(IOException expected){}
        assertEquals(1,directory.list().length);assertArrayEquals(new byte[]{1,2,3},files.read(id));
    }
    @Test public void enforcesReadAndWriteLimits() throws Exception {
        File directory=temporary.newFolder();GifFiles files=new GifFiles(directory);String id=files.put(new byte[]{1});Files.write(new File(directory,id+".gif").toPath(),new byte[GifFiles.MAX_FILE_BYTES+1]);
        try{files.read(id);fail();}catch(IOException expected){}
        try{files.put(new byte[GifFiles.MAX_FILE_BYTES+1]);fail();}catch(IOException expected){}
        try{files.put(new byte[0]);fail();}catch(IOException expected){}
    }
    @Test public void missingDirectoryIsEmptyAndUnreadableShapeIsError() throws Exception {
        File directory=new File(temporary.getRoot(),"missing");GifFiles files=new GifFiles(directory);assertTrue(files.ids().isEmpty());Files.write(directory.toPath(),new byte[]{1});try{files.ids();fail();}catch(IOException expected){}
    }
    @Test public void itemLimitAndOrphanTemporaryBytesCannotBeBypassed() throws Exception {
        File directory=temporary.newFolder();GifFiles files=new GifFiles(directory,1,100);files.put(new byte[]{1});
        try{files.put(new byte[]{2});fail();}catch(IOException expected){}
        File other=temporary.newFolder();Files.write(new File(other,"old-import.tmp").toPath(),new byte[4]);
        try{new GifFiles(other,100,4).put(new byte[]{1});fail();}catch(IOException expected){}
        assertEquals(1,other.list().length);
    }
    @Test public void concurrentAdaptersCannotBothSpendLastQuota() throws Exception {
        File directory=temporary.newFolder();GifFiles first=new GifFiles(directory,100,4),second=new GifFiles(directory,100,4);
        java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2);java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Future<Boolean> a=workers.submit(()->{start.await();try{first.put(new byte[]{1,1,1});return true;}catch(IOException expected){return false;}});
            java.util.concurrent.Future<Boolean> b=workers.submit(()->{start.await();try{second.put(new byte[]{2,2,2});return true;}catch(IOException expected){return false;}});
            start.countDown();boolean one=a.get(5,java.util.concurrent.TimeUnit.SECONDS),two=b.get(5,java.util.concurrent.TimeUnit.SECONDS);assertNotEquals(one,two);assertEquals(1,first.ids().size());assertEquals(3,first.read(first.ids().get(0)).length);assertEquals(1,directory.list().length);
        }finally{workers.shutdownNow();}
    }
}
