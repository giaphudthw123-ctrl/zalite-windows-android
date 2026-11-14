package org.ctdozlite.mobile;

import java.net.URI;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UpdateClientTest {
    @Test public void acceptsOnlyExactReleaseAsset() throws Exception {
        String repo = "minhkhang1008/ctdozlite-next";
        String tag = "v0.3.2";
        String asset = "ctdozlite-fork-arm64-v8a.apk";
        assertTrue(UpdateClient.isReleaseAsset(new URI("https://github.com/" + repo + "/releases/download/" + tag + "/" + asset), repo, tag, asset));
        assertFalse(UpdateClient.isReleaseAsset(new URI("https://github.com.evil.test/" + repo + "/releases/download/" + tag + "/" + asset), repo, tag, asset));
        assertFalse(UpdateClient.isReleaseAsset(new URI("https://github.com/" + repo + "/releases/download/v0.3.1/" + asset), repo, tag, asset));
        assertFalse(UpdateClient.isReleaseAsset(new URI("https://github.com/" + repo + "/releases/download/" + tag + "/" + asset + "?token=x"), repo, tag, asset));
        assertFalse(UpdateClient.isReleaseAsset(new URI("http://github.com/" + repo + "/releases/download/" + tag + "/" + asset), repo, tag, asset));
    }
}
