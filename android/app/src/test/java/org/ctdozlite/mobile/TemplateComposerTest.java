package org.ctdozlite.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class TemplateComposerTest {
    @Test public void insertsAtCaretOrReplacesSelectionWithoutSending() {
        assertEquals("Chào An!",TemplateComposer.insert("Chào !","Chào !","An",5,5));
        assertEquals("Chào Bình!",TemplateComposer.insert("Chào An!","Chào An!","Bình",5,7));
    }
    @Test public void preservesNewDraftAndRejectsOversizedExpansion() {
        try{TemplateComposer.insert("mới","cũ","text",0,0);fail();}catch(IllegalArgumentException expected){}
        try{TemplateComposer.insert("a","a","x".repeat(4000),1,1);fail();}catch(IllegalArgumentException expected){}
        assertEquals(4000,TemplateComposer.insert("a","a","x".repeat(3999),1,1).length());
    }
    @Test public void rejectsHalfEmojiAndInvalidSelection() {
        assertEquals("A😀XB",TemplateComposer.insert("A😀B","A😀B","X",3,3));
        for(int[] selection:new int[][]{{2,2},{-1,0},{0,9},{3,1}}){try{TemplateComposer.insert("A😀B","A😀B","X",selection[0],selection[1]);fail();}catch(IllegalArgumentException expected){}}
    }
}
