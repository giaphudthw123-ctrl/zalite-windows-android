package org.ctdozlite.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureSettingsTest {
    @Test public void recognizesExplicitEnabledAndDisabled() throws Exception {
        assertTrue(CaptureSettings.decode(new byte[]{'Z','C',1,1}));
        assertFalse(CaptureSettings.decode(new byte[]{'Z','C',1,0}));
    }
    @Test public void refusesTruncatedOrTrailingData() {
        for(byte[] value:new byte[][]{{},{'Z'},{'Z','C',1},{'Z','C',1,0,0}})reject(value);
    }
    @Test public void refusesUnknownFormatAndBoolean() {
        reject(new byte[]{'X','C',1,0});reject(new byte[]{'Z','X',1,0});reject(new byte[]{'Z','C',2,0});reject(new byte[]{'Z','C',1,2});reject(new byte[]{'Z','C',1,-1});
    }
    private void reject(byte[] value) {
        try{CaptureSettings.decode(value);fail("Invalid policy accepted");}catch(java.io.IOException expected){}
    }
}
