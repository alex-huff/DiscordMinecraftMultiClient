package dev.phonis.cosmicafkclient;

import dev.phonis.cosmicafkclient.auth.Authenticator;
import dev.phonis.cosmicafkclient.client.LoginQueue;
import dev.phonis.cosmicafkclient.client.MultiClient;
import dev.phonis.cosmicafkclient.discord.DiscordManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class CosmicAFKClient {

    public static final Set<String> whitelisted = new ConcurrentSkipListSet<>();
    public static MultiClient multiClient;
    public static DiscordManager dm;
    private static final File accountsFile = new File("accounts.txt");
    private static final File whitelistFile = new File("whitelist.txt");
    private static final File tokenFile = new File("token.txt");

    static {
        try {
            if (CosmicAFKClient.whitelistFile.createNewFile()) System.out.println("Creating whitelist file");

            FileInputStream fileInputStream = new FileInputStream(CosmicAFKClient.whitelistFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] params = line.split(" ");

                CosmicAFKClient.whitelisted.add(params[0]);
            }

            reader.close();
        } catch (IOException e) {
            CosmicAFKClient.log("Failed to read " + CosmicAFKClient.whitelistFile.getName());
        }
    }

    public static void embedWithPlayer(String player, String title, String message) {
        if (message.isEmpty() || message.isBlank()) return;

        if (player.isEmpty() || player.isBlank()) return;

        if (CosmicAFKClient.dm == null) return;

        CosmicAFKClient.dm.sendPlayerEmbed(player, title, message);
    }

    public static void log(String message) {
        if (message == null) return;

        if (message.isEmpty() || message.isBlank()) return;

        if (CosmicAFKClient.dm != null) {
            CosmicAFKClient.dm.sendMessage(message);
        } else {
            System.out.println(message); // for before DiscordManager is initialized
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CosmicAFKClient.multiClient = MultiClient.fromFile(CosmicAFKClient.accountsFile);
        CosmicAFKClient.dm = new DiscordManager(CosmicAFKClient.getDiscordToken());

        CosmicAFKClient.multiClient.startClients();
    }

    public static void shutdown() throws FileNotFoundException, InterruptedException {
        CosmicAFKClient.multiClient.toFile(CosmicAFKClient.accountsFile);
        CosmicAFKClient.saveWhitelist();
        LoginQueue.restarterThread.interrupt();
        Authenticator.sessionThread.interrupt();
        LoginQueue.restarterThread.join();
        Authenticator.sessionThread.join();
    }

    private static String getDiscordToken() throws IOException {
        if (CosmicAFKClient.tokenFile.createNewFile()) System.out.println("Creating token file");

        FileInputStream fileInputStream = new FileInputStream(CosmicAFKClient.tokenFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));
        String ret = reader.readLine();

        reader.close();

        return ret;
    }

    private static void saveWhitelist() throws FileNotFoundException {
        FileOutputStream fileOutputStream = new FileOutputStream(CosmicAFKClient.whitelistFile);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8));

        try {
            for (String player : CosmicAFKClient.whitelisted) {
                writer.write(player + '\n');
            }
        } catch (IOException e) {
            CosmicAFKClient.log("Failed to write whitelist file");
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
