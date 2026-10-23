package com.columnaspine.weasis.spinopelvic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up the latest GitHub release and, on request, installs its bundle in place: the new jar
 * goes next to the current one in ~/.weasis/plugins, espinopelvico.json is pointed at it and the
 * old jar is removed, which is what the installers do. Weasis picks it up on its next start.
 */
final class Updater {
  static final String RELEASES_PAGE =
      "https://github.com/bersonzini-glitch/weasis-parametros-espinopelvicos/releases/latest";
  private static final String API =
      "https://api.github.com/repos/bersonzini-glitch/weasis-parametros-espinopelvicos/releases/latest";
  private static final Pattern JAR_NAME = Pattern.compile("weasis-espinopelvico-[0-9.]+\\.jar");

  record Release(String version, String notes, String jarUrl, String jarName) {}

  private static final HttpClient HTTP =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
  private static CompletableFuture<Release> check;

  private Updater() {}

  /** Once per Weasis session; completes with null when there is nothing newer (or no network). */
  static synchronized CompletableFuture<Release> latest(String currentVersion) {
    if (check == null) {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(API)).header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(15)).build();
      check =
          HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
              .thenApply(r -> r.statusCode() == 200 ? parse(r.body()) : null)
              .thenApply(rel -> rel != null && isNewer(rel.version(), currentVersion) ? rel : null)
              .exceptionally(e -> null);
      // A failed lookup is tried again the next time the panel is built.
      check.thenAccept(rel -> { if (rel == null) clearCheck(); });
    }
    return check;
  }

  private static synchronized void clearCheck() {
    check = null;
  }

  static Release parse(String json) {
    String tag = field(json, "tag_name");
    String jarUrl = null;
    Matcher m = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
    while (m.find()) {
      if (JAR_NAME.matcher(m.group(1)).find()) jarUrl = m.group(1);
    }
    if (tag == null || jarUrl == null) return null;
    Matcher name = JAR_NAME.matcher(jarUrl);
    name.find();
    String notes = field(json, "body");
    return new Release(tag.replaceFirst("^[vV]", ""), notes == null ? "" : notes, jarUrl, name.group());
  }

  /** A JSON string field, with the escapes GitHub uses in release notes. */
  private static String field(String json, String key) {
    Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
    if (!m.find()) return null;
    String s = m.group(1);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != '\\' || i + 1 >= s.length()) { out.append(c); continue; }
      char e = s.charAt(++i);
      switch (e) {
        case 'n' -> out.append('\n');
        case 'r' -> {}
        case 't' -> out.append('\t');
        case 'u' -> {
          out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
          i += 4;
        }
        default -> out.append(e);
      }
    }
    return out.toString();
  }

  private static boolean reported;

  /**
   * Once per Weasis session: tells sagitalapp.com about a first install or a new version, so the
   * author can count installs. Sends the plugin and Weasis versions, the OS and a random id kept
   * in the Java user preferences; the server derives an approximate location from the connection
   * and stores no IP.
   */
  static synchronized void reportInstall(String version) {
    if (reported) return;
    reported = true;
    java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userRoot().node("com/columnaspine/sagitalapp");
    String id = prefs.get("instalacion_id", null), previous = prefs.get("version_reportada", null);
    if (id != null && version.equals(previous)) return;
    String newId = id != null ? id : java.util.UUID.randomUUID().toString();
    String os = System.getProperty("os.name", "").toLowerCase();
    String so = os.contains("mac") ? "mac" : os.contains("win") ? "windows" : "linux";
    String json =
        "{\"plugin\":\"weasis\",\"evento\":\"" + (id == null ? "instalacion" : "actualizacion")
            + "\",\"version\":\"" + version + "\",\"so\":\"" + so
            + "\",\"app_version\":\"" + System.getProperty("weasis.version", "").replaceAll("[^0-9A-Za-z.-]", "")
            + "\",\"instalacion_id\":\"" + newId + "\""
            + (previous == null ? "" : ",\"version_anterior\":\"" + previous + "\"") + "}";
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("https://sagitalapp.com/api/instalacion"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding())
        .thenAccept(r -> {
          if (r.statusCode() / 100 != 2) return; // try again next session
          prefs.put("instalacion_id", newId);
          prefs.put("version_reportada", version);
          try {
            prefs.flush();
          } catch (Exception ignored) {
            // Preferences are saved on exit anyway.
          }
        });
  }

  private static java.util.prefs.Preferences prefs() {
    return java.util.prefs.Preferences.userRoot().node("com/columnaspine/sagitalapp");
  }

  /** The last message received from sagitalapp.com, shown while offline ("" when none). */
  static String cachedNotice() {
    return prefs().get("aviso", "");
  }

  /** The author's current message; completes with null when offline, so the cached one stays. */
  static CompletableFuture<String> fetchNotice() {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("https://sagitalapp.com/api/aviso"))
            .header("Cache-Control", "no-cache")
            .timeout(Duration.ofSeconds(15))
            .build();
    return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
        .thenApply(r -> r.statusCode() == 200 ? field(r.body(), "texto") : null)
        .thenApply(text -> {
          if (text != null) {
            prefs().put("aviso", text);
            try {
              prefs().flush();
            } catch (Exception ignored) {
              // Saved on exit anyway.
            }
          }
          return text;
        })
        .exceptionally(e -> null);
  }

  /** "1.1.0" > "1.0.2": numeric components, missing ones count as 0. */
  static boolean isNewer(String candidate, String current) {
    String[] a = candidate.split("\\."), b = current.split("\\.");
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      int x = i < a.length ? num(a[i]) : 0, y = i < b.length ? num(b[i]) : 0;
      if (x != y) return x > y;
    }
    return false;
  }

  private static int num(String s) {
    String d = s.replaceAll("\\D.*", "");
    return d.isEmpty() ? 0 : Integer.parseInt(d);
  }

  /**
   * Downloads the release's jar into the folder of the running bundle and switches the Weasis
   * config to it. {@code bundleLocation} is the running bundle's file: URI.
   */
  static void install(Release rel, String bundleLocation) throws Exception {
    Path current = Path.of(URI.create(bundleLocation));
    Path dir = current.getParent();
    Path config = dir.resolve("espinopelvico.json");
    if (!Files.isRegularFile(config)) throw new IllegalStateException("No encuentro " + config);

    Path part = dir.resolve(rel.jarName() + ".part");
    HttpResponse<Path> r =
        HTTP.send(HttpRequest.newBuilder(URI.create(rel.jarUrl())).timeout(Duration.ofMinutes(2)).build(),
            HttpResponse.BodyHandlers.ofFile(part));
    if (r.statusCode() != 200 || Files.size(part) < 1000) {
      Files.deleteIfExists(part);
      throw new IllegalStateException("La descarga respondió " + r.statusCode());
    }
    Path jar = dir.resolve(rel.jarName());
    Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);

    String json = Files.readString(config);
    Files.writeString(config, JAR_NAME.matcher(json).replaceAll(Matcher.quoteReplacement(rel.jarName())));
    if (!jar.equals(current)) {
      try {
        Files.deleteIfExists(current);
      } catch (Exception ignored) {
        // Windows may keep it open until Weasis quits; the config no longer points at it.
      }
    }
  }
}
