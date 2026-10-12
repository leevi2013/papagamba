package de.bluegamba;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Lokales Demo-Guthaben. Dies ist absichtlich keine Server- oder Echtgeld-Waehrung. */
final class GambaAccount {
    private static final long START_BALANCE = 1_000L;
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("bluegamba.properties");

    static long balance = START_BALANCE;

    private GambaAccount() {
    }

    static void load() {
        if (!Files.exists(FILE)) {
            balance = START_BALANCE;
            save();
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            balance = Math.max(0L, Long.parseLong(properties.getProperty("balance", "1000")));
        } catch (Exception exception) {
            balance = START_BALANCE;
            System.err.println("[Blue Gamba] Guthaben konnte nicht gelesen werden; starte mit 1000 Coins.");
        }
    }

    static void save() {
        Properties properties = new Properties();
        properties.setProperty("balance", Long.toString(Math.max(0L, balance)));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Blue Gamba local demo balance; not a server economy");
            }
        } catch (Exception exception) {
            System.err.println("[Blue Gamba] Guthaben konnte nicht gespeichert werden.");
        }
    }
}
