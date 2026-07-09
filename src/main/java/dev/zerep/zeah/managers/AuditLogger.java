package dev.zerep.zeah.managers;

import dev.zerep.zeah.ZeAuctionHouse;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class AuditLogger {

    private final ZeAuctionHouse plugin;
    private final boolean enabled;
    private final int retentionDays;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AuditLogger(ZeAuctionHouse plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("log.enabled", true);
        this.retentionDays = plugin.getConfig().getInt("log.rotation-days", 30);
    }

    public void log(String type, UUID actor, String details) {
        if (!enabled) return;
        plugin.getDb().insertAuditLog(type, actor, details);
        writeToFile(type, actor.toString(), details);
    }

    private void writeToFile(String type, String actor, String details) {
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) logsDir.mkdirs();

        String filename = "transactions-" + LocalDate.now() + ".log";
        File logFile = new File(logsDir, filename);

        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.printf("[%s] [%s] %s | %s%n",
                LocalDateTime.now().format(DT), type, actor, details);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write audit log: " + e.getMessage());
        }
        purgeOldLogs(logsDir);
    }

    private void purgeOldLogs(File logsDir) {
        File[] files = logsDir.listFiles((d, n) -> n.startsWith("transactions-") && n.endsWith(".log"));
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - (retentionDays * 86400_000L);
        for (File f : files) {
            if (f.lastModified() < cutoff) f.delete();
        }
    }
}
