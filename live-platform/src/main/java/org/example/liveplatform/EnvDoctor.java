package org.example.liveplatform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * EnvDoctor - (Windows/macOS/Linux)
 * Detects: Java, Git, Maven, Docker, Docker Compose
 * Features: Detailed diagnostics, Windows-friendly (uses cmd /c for mvn.cmd), PATH fallback, timeout protection.
 */
public class EnvDoctor {

  // ===== Configuration =====
  static final boolean VERBOSE = true;          // Print detailed diagnostics
  static final int MIN_JAVA_MAJOR   = 21;       // Minimum major version required (modify as needed)
  static final int MIN_GIT_MAJOR    = 2;
  static final int MIN_DOCKER_MAJOR = 25;

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("Dev Doctor (Java) - Environment Check");
    System.out.println("==================================================");

    // Basic environment
    logBasicEnv();
    logPathEntries();

    // where/which initial diagnostics
    logWhereWhich(osIsWindows() ? "java.exe" : "java");
    logWhereWhich(osIsWindows() ? "git.exe" : "git");
    logWhereWhich(osIsWindows() ? "mvn.cmd" : "mvn");
    logWhereWhich(osIsWindows() ? "docker.exe" : "docker");
    if (osIsWindows()) logWhereWhich("docker-compose.exe");
    else               logWhereWhich("docker-compose");

    // ===== 1) Java =====
    JavaVer jv = detectJavaVersion();
    boolean javaOk = jv.major >= MIN_JAVA_MAJOR;

    // ===== 2) Git =====
    CmdResult git = detectGit();
    int gitMajor = parseMajor(git.version);
    boolean gitOk = git.present && gitMajor >= MIN_GIT_MAJOR;

    // ===== 3) Maven (multi-strategy + Windows shell + absolute path + PATH fallback) =====
    CmdResult mvn = detectMavenVerbose();

    // ===== 4) Docker =====
    CmdResult dockerVersion = runCmd("docker", Arrays.asList("--version"), Duration.ofSeconds(8), VERBOSE);
    int dockerMajor = parseMajor(dockerVersion.version);
    boolean dockerPresent = dockerVersion.present;
    boolean dockerOk = dockerPresent && dockerMajor >= MIN_DOCKER_MAJOR;

    CmdResult dockerInfo = runCmd("docker", Arrays.asList("info"), Duration.ofSeconds(10), VERBOSE);
    boolean dockerRunning = dockerInfo.exitCode == 0;

    // ===== 5) Compose (v2: docker compose; v1: docker-compose) =====
    CmdResult composeV2 = runCmd("docker", Arrays.asList("compose", "version"), Duration.ofSeconds(8), VERBOSE);
    CmdResult composeV1 = runCmd(osIsWindows() ? "docker-compose.exe" : "docker-compose",
        Arrays.asList("--version"), Duration.ofSeconds(8), VERBOSE);
    boolean composePresent = composeV2.present || composeV1.present;
    String composeVersion = composeV2.present ? composeV2.version : composeV1.version;

    // ===== Summary Output =====
    System.out.println();
    System.out.println("----------------- SUMMARY -----------------");
    System.out.printf("[Java]     version=%s (major=%d)  %s (>= %d)%n",
        jv.raw, jv.major, ok(javaOk), MIN_JAVA_MAJOR);

    System.out.printf("[Git]      present=%s, version=%s (major=%d)  %s (>= %d)%n",
        git.present, val(git.version), gitMajor, ok(gitOk), MIN_GIT_MAJOR);

    System.out.printf("[Maven]    present=%s, version=%s%n",
        mvn.present, val(mvn.version));
    System.out.printf("           env=%s%n", envStr("MAVEN_HOME", "M2_HOME"));
    System.out.printf("           settings.xml=%s%n", detectMavenSettings().map(Path::toString).orElse("(not found)"));

    System.out.printf("[Docker]   present=%s, version=%s (major=%d)  %s (>= %d)%n",
        dockerPresent, val(dockerVersion.version), dockerMajor, ok(dockerOk), MIN_DOCKER_MAJOR);
    System.out.printf("           daemon running=%s%n", dockerRunning);

    System.out.printf("[Compose]  present=%s, version=%s (v2-via-docker=%s, v1-binary=%s)%n",
        composePresent, val(composeVersion), composeV2.present, composeV1.present);
    System.out.println("-------------------------------------------");

    // ===== Smart Hints =====
    suggestFixes(jv, git, mvn, dockerVersion, dockerInfo);

    boolean overall = javaOk && gitOk && mvn.present && dockerOk && dockerRunning;
    System.exit(overall ? 0 : 1);
  }

  // ===================== Detection Implementation =====================

  static JavaVer detectJavaVersion() {
    try {
      // Java 9+
      Runtime.Version v = Runtime.version();
      return new JavaVer(v.feature(), v.toString());
    } catch (Throwable ignore) {
      // Java 8 compatibility
      String spec = System.getProperty("java.specification.version", "");
      int major = parseJavaMajorCompat(spec);
      return new JavaVer(major, spec);
    }
  }

  static int parseJavaMajorCompat(String spec) {
    try {
      if (spec.startsWith("1.")) return Integer.parseInt(spec.substring(2));
      return Integer.parseInt(spec);
    } catch (Exception e) { return 0; }
  }

  static CmdResult detectGit() {
    // Git can usually be run directly
    return runCmd(osIsWindows() ? "git.exe" : "git", Arrays.asList("--version"),
        Duration.ofSeconds(5), VERBOSE);
  }

  /** Robust Maven detection (multi-strategy + Windows shell + absolute path + PATH fallback) */
  static CmdResult detectMavenVerbose() {
    Duration t = Duration.ofSeconds(8);

    // A. Preferred: Use shell for Windows (mvn/mvn.cmd), direct mvn for other platforms
    if (osIsWindows()) {
      CmdResult r = runShell(Arrays.asList("cmd", "/c", "mvn", "-v"), t, VERBOSE);
      if (r.present) return r;
      r = runShell(Arrays.asList("cmd", "/c", "mvn.cmd", "-v"), t, VERBOSE);
      if (r.present) return r;
    } else {
      CmdResult r = runShell(Arrays.asList("mvn", "-v"), t, VERBOSE);
      if (r.present) return r;
    }

    // B. Absolute path fallback: MAVEN_HOME/M2_HOME/bin
    String mh = System.getenv("MAVEN_HOME");
    if (mh == null || mh.isBlank()) mh = System.getenv("M2_HOME");
    if (mh != null && !mh.isBlank()) {
      Path bin = Paths.get(mh, "bin");
      if (osIsWindows()) {
        Path cmd = bin.resolve("mvn.cmd");
        if (Files.isRegularFile(cmd)) {
          CmdResult r2 = runShell(Arrays.asList("cmd", "/c", cmd.toString(), "-v"), t, VERBOSE);
          if (r2.present) return r2;
        }
        Path bat = bin.resolve("mvn.bat");
        if (Files.isRegularFile(bat)) {
          CmdResult r3 = runShell(Arrays.asList("cmd", "/c", bat.toString(), "-v"), t, VERBOSE);
          if (r3.present) return r3;
        }
      } else {
        Path exe = bin.resolve("mvn");
        if (Files.isRegularFile(exe)) {
          exe.toFile().setExecutable(true, false);
          CmdResult r4 = runShell(Arrays.asList(exe.toString(), "-v"), t, VERBOSE);
          if (r4.present) return r4;
        }
      }
    }

    // C. Fallback failed: return an unavailable result (constructed with stderr info)
    return new CmdResult(false, 127, "", "", "mvn -v", "mvn not found in PATH/MAVEN_HOME");
  }

  static Optional<Path> detectMavenSettings() {
    Path home = Paths.get(System.getProperty("user.home", ""));
    Path userSettings = home.resolve(".m2").resolve("settings.xml");
    if (Files.isRegularFile(userSettings)) return Optional.of(userSettings);

    String mh = System.getenv("MAVEN_HOME");
    if (mh == null || mh.isBlank()) mh = System.getenv("M2_HOME");
    if (mh != null && !mh.isBlank()) {
      Path p = Paths.get(mh).resolve("conf").resolve("settings.xml");
      if (Files.isRegularFile(p)) return Optional.of(p);
    }
    return Optional.empty();
  }

  // ===================== Process & Diagnostics =====================

  /** Preferred: Run command directly; Windows will try .exe names; automatically appends common PATH entries */
  static CmdResult runCmd(String exe, List<String> args, Duration timeout, boolean verbose) {
    List<String> cmd = new ArrayList<>();
    cmd.add(exe);
    cmd.addAll(args);
    return runShell(cmd, timeout, verbose);
  }

  /** Unified subprocess execution: auto-appends PATH, concurrently reads stdout/stderr, timeout, verbose logging */
  static CmdResult runShell(List<String> cmd, Duration timeout, boolean verbose) {
    ProcessBuilder pb = new ProcessBuilder(cmd);

    // ---- Temporarily append PATH (does not overwrite, only prepends) ----
    Map<String, String> env = pb.environment();
    String path = env.getOrDefault("PATH", "");
    String sep  = osIsWindows() ? ";" : ":";

    List<String> extras = new ArrayList<>();
    if (osIsWindows()) {
      // Common Docker Desktop directory
      extras.add("C:\\Program Files\\Docker\\Docker\\resources\\bin");
      // Maven bin from MAVEN_HOME/M2_HOME
      String mh = System.getenv("MAVEN_HOME");
      if (mh == null || mh.isBlank()) mh = System.getenv("M2_HOME");
      if (mh != null && !mh.isBlank()) extras.add(mh + "\\bin");
      // Common system locations (optional)
      extras.add("C:\\Program Files\\Git\\bin");
    } else {
      extras.add("/usr/local/bin");
      extras.add("/usr/bin");
      String mh = System.getenv("MAVEN_HOME");
      if (mh == null || mh.isBlank()) mh = System.getenv("M2_HOME");
      if (mh != null && !mh.isBlank()) extras.add(mh + "/bin");
    }
    for (String extra : extras) {
      if (extra != null && !extra.isBlank()
          && Arrays.stream(path.split(java.util.regex.Pattern.quote(sep)))
          .noneMatch(p -> p.equalsIgnoreCase(extra))) {
        path = extra + sep + path;
      }
    }
    env.put("PATH", path);
    // ---- End of temporary PATH append ----

    if (verbose) {
      System.out.println(">> " + String.join(" ", cmd));
    }

    try {
      Process p = pb.start();

      Future<String> outF = readAsync(p.getInputStream());
      Future<String> errF = readAsync(p.getErrorStream());

      boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        p.destroyForcibly();
        if (verbose) System.out.println("!! TIMEOUT after " + timeout.getSeconds() + "s");
        return new CmdResult(false, -1, "", "TIMEOUT",
            String.join(" ", cmd), "TIMEOUT");
      }

      String stdout = safeGet(outF);
      String stderr = safeGet(errF);
      int exit = p.exitValue();

      if (verbose) {
        if (!stdout.isBlank()) {
          System.out.println("-- stdout --");
          System.out.println(stdout.trim());
        }
        if (!stderr.isBlank()) {
          System.out.println("-- stderr --");
          System.out.println(stderr.trim());
        }
        System.out.println("-- exit --");
        System.out.println(exit);
        System.out.println();
      }

      boolean present = exit != 127 && exit != 9009; // 127: *nix not found; 9009: cmd not found
      String merged = (stdout + "\n" + stderr).trim();
      String version = extractVersion(merged);

      return new CmdResult(present, exit, stdout, version, String.join(" ", cmd), stderr);

    } catch (IOException ioe) {
      if (verbose) {
        System.out.println("IOException (likely PATH/permission): " + ioe.getMessage());
      }
      return new CmdResult(false, 127, "", "", String.join(" ", cmd), ioe.toString());
    } catch (Exception e) {
      return new CmdResult(false, -2, "", "", String.join(" ", cmd), e.toString());
    }
  }

  static Future<String> readAsync(InputStream in) {
    ExecutorService es = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "envdoctor-io");
      t.setDaemon(true);
      return t;
    });
    return es.submit(() -> {
      try (InputStream is = in) {
        return new String(is.readAllBytes(), Charset.defaultCharset());
      } finally {
        es.shutdown();
      }
    });
  }

  static String safeGet(Future<String> f) {
    try { return f.get(200, TimeUnit.MILLISECONDS); }
    catch (Exception ignore) { return ""; }
  }

  // ===================== Utilities & Parsing =====================

  static void logBasicEnv() {
    System.out.printf("OS          : %s%n", System.getProperty("os.name"));
    System.out.printf("OS Version  : %s%n", System.getProperty("os.version"));
    System.out.printf("Arch        : %s%n", System.getProperty("os.arch"));
    System.out.printf("User        : %s%n", System.getProperty("user.name"));
    System.out.printf("Home        : %s%n", System.getProperty("user.home"));
    System.out.printf("Work Dir    : %s%n", Paths.get("").toAbsolutePath());
    System.out.println();
  }

  static void logPathEntries() {
    System.out.println("PATH entries (Java process):");
    String sep = osIsWindows() ? ";" : ":";
    String path = System.getenv("PATH");
    if (path == null) {
      System.out.println("(PATH is null)");
      System.out.println();
      return;
    }
    int i = 0;
    for (String p : path.split(java.util.regex.Pattern.quote(sep))) {
      System.out.printf("  %2d) %s%n", ++i, p);
    }
    System.out.println();
  }

  static void logWhereWhich(String exe) {
    if (osIsWindows()) {
      System.out.println("Running: cmd /c where " + exe);
      CmdResult r = runShell(Arrays.asList("cmd", "/c", "where", exe), Duration.ofSeconds(5), VERBOSE);
      // Note: Program output remains in Chinese
      if (r.exitCode != 0) System.out.println("where 未找到该命令（可能不在 PATH 中）。\n");
    } else {
      System.out.println("Running: which " + exe);
      CmdResult r = runShell(Arrays.asList("which", exe), Duration.ofSeconds(5), VERBOSE);
      // Note: Program output remains in Chinese
      if (r.exitCode != 0) System.out.println("which 未找到该命令（可能不在 PATH 中）。\n");
    }
  }

  static void suggestFixes(JavaVer jv, CmdResult git, CmdResult mvn,
                           CmdResult dockerVersion, CmdResult dockerInfo) {
    System.out.println();
    System.out.println("Hints:");
    // Note: Program output hints remain in Chinese
    if (jv.major < MIN_JAVA_MAJOR) {
      System.out.printf("- Java 版本过低（%s），建议升级到 %d+%n", jv.raw, MIN_JAVA_MAJOR);
    }
    if (!git.present) {
      System.out.println("- 未检测到 Git：请确认已安装并将其 bin 目录加入 PATH。");
    }
    if (!mvn.present) {
      System.out.println("- 未检测到 Maven 命令：");
      System.out.println("  * Windows：确保 %MAVEN_HOME%/bin 在 PATH 中，或使用 mvn.cmd。");
      System.out.println("  * 你也可仅配置 M2_HOME=Maven 根目录，并将其 bin 追加到 PATH。");
    }
    if (!dockerVersion.present) {
      System.out.println("- Java 进程无法找到 'docker' 可执行：");
      System.out.println("  * Windows 常见路径：C:\\Program Files\\Docker\\Docker\\resources\\bin");
      System.out.println("  * 在 IDE 的 Run 配置里追加 PATH 或重启 IDE 继承系统 PATH。");
    } else if (dockerInfo.exitCode != 0) {
      System.out.println("- 'docker' 存在，但 daemon 未就绪：请启动 Docker Desktop（或相应服务）。");
    }
    System.out.println();
  }

  static String extractVersion(String text) {
    // Matches x.y or x.y.z (with optional 'v' prefix)
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?")
        .matcher(text);
    if (m.find()) {
      StringBuilder sb = new StringBuilder(m.group(1));
      if (m.group(2) != null) sb.append(".").append(m.group(2));
      if (m.group(3) != null) sb.append(".").append(m.group(3));
      return sb.toString();
    }
    return "";
  }

  static int parseMajor(String version) {
    if (version == null || version.isBlank()) return 0;
    int dot = version.indexOf('.');
    String major = (dot > 0) ? version.substring(0, dot) : version;
    try { return Integer.parseInt(major.replaceAll("\\D+", "")); }
    catch (Exception e) { return 0; }
  }

  static boolean osIsWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  static String ok(boolean b) { return b ? "[OK]" : "[NOT OK]"; }
  static String val(String s) { return (s==null || s.isBlank()) ? "(unknown)" : s; }

  static String envStr(String... keys) {
    for (String k : keys) {
      String v = System.getenv(k);
      if (v != null && !v.isBlank()) return k + "=" + v;
    }
    return "(none)";
  }

  // ===================== Data Structures =====================

  static class JavaVer {
    final int major;
    final String raw;
    JavaVer(int major, String raw) { this.major = major; this.raw = raw; }
  }

  static class CmdResult {
    final boolean present;   // Can the command be started (basically means "installed / in PATH")
    final int exitCode;      // Exit code
    final String stdout;     // Standard output
    final String version;    // Extracted version from output (may be empty)
    final String cmd;        // The command that was executed
    final String stderr;     // Standard error

    CmdResult(boolean present, int exitCode, String stdout, String version, String cmd, String stderr) {
      this.present = present;
      this.exitCode = exitCode;
      this.stdout = stdout;
      this.version = version;
      this.cmd = cmd;
      this.stderr = stderr;
    }
  }
}
