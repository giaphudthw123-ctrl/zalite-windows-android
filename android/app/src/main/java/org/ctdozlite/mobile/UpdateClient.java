package org.ctdozlite.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

/** Manual, cookie-free GitHub release updater. It never invokes the installer itself. */
public final class UpdateClient {
    private static final int MAX_METADATA = 256 * 1024;
    private static final long MAX_APK = 120L * 1024 * 1024;

    public static final class Result {
        public final String tag;
        public final File apk;
        Result(String tag, File apk) { this.tag = tag; this.apk = apk; }
    }

    private UpdateClient() { }

    public static Result checkAndDownload(String repo, String assetName, File cacheDir) throws Exception {
        if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Repo cập nhật chưa được cấu hình");
        if (!assetName.matches("[A-Za-z0-9_.-]+\\.apk")) throw new IllegalArgumentException("Tên APK không hợp lệ");
        URI api = new URI("https://api.github.com/repos/" + repo + "/releases/latest");
        JSONObject release = new JSONObject(readText(api, MAX_METADATA));
        String tag = release.optString("tag_name", "");
        if (!tag.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("Tag phát hành không hợp lệ");
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) throw new IllegalArgumentException("Release chưa có tệp tải");
        JSONObject apkAsset = null;
        JSONObject sumsAsset = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject item = assets.optJSONObject(i);
            if (item == null) continue;
            if (assetName.equals(item.optString("name"))) apkAsset = item;
            if ("SHA256SUMS.txt".equals(item.optString("name"))) sumsAsset = item;
        }
        if (apkAsset == null) throw new IllegalArgumentException("Repo này chưa phát hành APK cho kênh này");
        URI apkUrl = new URI(apkAsset.getString("browser_download_url"));
        if (!isReleaseAsset(apkUrl, repo, tag, assetName)) throw new IllegalArgumentException("Nguồn APK không hợp lệ");
        String digest = apkAsset.optString("digest", "");
        String expected = digest.matches("sha256:[0-9a-fA-F]{64}") ? digest.substring(7) : "";
        if (expected.isEmpty() && sumsAsset != null) {
            URI sumsUrl = new URI(sumsAsset.getString("browser_download_url"));
            if (!isReleaseAsset(sumsUrl, repo, tag, "SHA256SUMS.txt")) throw new IllegalArgumentException("Nguồn checksum không hợp lệ");
            String sums = readText(sumsUrl, MAX_METADATA);
            for (String line : sums.split("\\R")) {
                String[] fields = line.trim().split("\\s+\\*?", 2);
                if (fields.length == 2 && assetName.equals(fields[1]) && fields[0].matches("[0-9a-fA-F]{64}")) {
                    expected = fields[0];
                    break;
                }
            }
        }
        if (!expected.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Release thiếu SHA-256 cho APK");
        long size = apkAsset.optLong("size", -1);
        if (size <= 0 || size > MAX_APK) throw new IllegalArgumentException("APK vượt giới hạn 120 MB");
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) throw new IllegalArgumentException("Không tạo được cache cập nhật");
        File temp = new File(cacheDir, "verified-update.tmp");
        File target = new File(cacheDir, "verified-update.apk");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            HttpURLConnection connection = connect(apkUrl);
            long total = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_APK) throw new IllegalArgumentException("APK vượt giới hạn 120 MB");
                    hash.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            } finally { connection.disconnect(); }
            if (total != size || !hex(hash.digest()).equalsIgnoreCase(expected)) throw new IllegalArgumentException("APK không khớp kích thước hoặc SHA-256");
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new Result(tag, target);
        } finally { if (temp.exists()) temp.delete(); }
    }

    static boolean isReleaseAsset(URI uri, String repo, String tag, String name) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
            || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return false;
        return ("/" + repo + "/releases/download/" + tag + "/" + name).equals(uri.getRawPath());
    }

    private static String readText(URI url, int limit) throws Exception {
        HttpURLConnection connection = connect(url);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new IllegalArgumentException("Metadata cập nhật quá lớn");
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        } finally { connection.disconnect(); }
    }

    private static HttpURLConnection connect(URI initial) throws Exception {
        URI current = initial;
        for (int redirect = 0; redirect <= 4; redirect++) {
            String host = current.getHost();
            if (!"https".equalsIgnoreCase(current.getScheme()) || host == null || current.getPort() != -1
                || current.getUserInfo() != null || !(host.equalsIgnoreCase("api.github.com")
                || host.equalsIgnoreCase("github.com") || host.equalsIgnoreCase("release-assets.githubusercontent.com"))) {
                throw new IllegalArgumentException("Chuyển hướng cập nhật không đáng tin cậy");
            }
            HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "ctdozlite-android-local/0.1");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            int status = connection.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IllegalArgumentException("Thiếu đích chuyển hướng");
                current = current.resolve(location);
                continue;
            }
            if (status != 200) {
                connection.disconnect();
                throw new IllegalArgumentException(status == 404 ? "Chưa có release công khai cho repo này" : "Không tải được release GitHub (HTTP " + status + ")");
            }
            return connection;
        }
        throw new IllegalArgumentException("Quá nhiều lần chuyển hướng cập nhật");
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }
}
