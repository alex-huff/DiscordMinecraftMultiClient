package dev.phonis.discordminecraftmulticlient;

import dev.phonis.discordminecraftmulticlient.auth.Authenticator;
import dev.phonis.discordminecraftmulticlient.client.LoginQueue;
import dev.phonis.discordminecraftmulticlient.client.McClient;
import dev.phonis.discordminecraftmulticlient.client.MultiClient;
import dev.phonis.discordminecraftmulticlient.discord.DiscordManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public
class DiscordMinecraftMultiClient
{

	public static final  Set<String>    whitelisted   = new ConcurrentSkipListSet<>();
	public static        MultiClient    multiClient;
	public static        DiscordManager dm;
	private static final File           accountsFile  = new File("accounts.txt");
	private static final File           whitelistFile = new File("whitelist.txt");
	private static final File           tokenFile     = new File("token.txt");

	static
	{
		try
		{
			if (DiscordMinecraftMultiClient.whitelistFile.createNewFile())
			{
				System.out.println("Creating whitelist file");
			}

			FileInputStream fileInputStream = new FileInputStream(DiscordMinecraftMultiClient.whitelistFile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));
			String line;

			while ((line = reader.readLine()) != null)
			{
				String[] params = line.split(" ");

				DiscordMinecraftMultiClient.whitelisted.add(params[0]);
			}

			reader.close();
		}
		catch (IOException e)
		{
			DiscordMinecraftMultiClient.log("Failed to read " + DiscordMinecraftMultiClient.whitelistFile.getName());
		}
	}

	public static
	void embedWithPlayer(String player, String title, String message)
	{
		if (message.isEmpty() || message.isBlank())
		{
			return;
		}

		if (player.isEmpty() || player.isBlank())
		{
			return;
		}

		if (DiscordMinecraftMultiClient.dm == null)
		{
			return;
		}

		DiscordMinecraftMultiClient.dm.sendPlayerEmbed(player, title, message);
	}

	public static
	void log(String message)
	{
		if (message == null)
		{
			return;
		}

		if (message.isEmpty() || message.isBlank())
		{
			return;
		}

		if (DiscordMinecraftMultiClient.dm != null)
		{
			DiscordMinecraftMultiClient.dm.sendMessage(message);
		}
		else
		{
			System.out.println(message); // for before DiscordManager is initialized
		}
	}

	public static
	void main(String[] args) throws IOException, InterruptedException
	{
		DiscordMinecraftMultiClient.multiClient = MultiClient.fromFile(DiscordMinecraftMultiClient.accountsFile);
		DiscordMinecraftMultiClient.dm          = new DiscordManager(DiscordMinecraftMultiClient.getDiscordToken());

		DiscordMinecraftMultiClient.multiClient.startClients();
	}

	public static
	void shutdown() throws FileNotFoundException, InterruptedException
	{
		DiscordMinecraftMultiClient.multiClient.toFile(DiscordMinecraftMultiClient.accountsFile);
		DiscordMinecraftMultiClient.saveWhitelist();
		LoginQueue.restarterThread.interrupt();
		Authenticator.sessionThread.interrupt();
		LoginQueue.restarterThread.join();
		Authenticator.sessionThread.join();
	}

	private static
	String getDiscordToken() throws IOException
	{
		if (DiscordMinecraftMultiClient.tokenFile.createNewFile())
		{
			System.out.println("Creating token file");
		}

		FileInputStream fileInputStream = new FileInputStream(DiscordMinecraftMultiClient.tokenFile);
		BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));
		String ret = reader.readLine();

		reader.close();

		return ret;
	}

	private static
	void saveWhitelist() throws FileNotFoundException
	{
		FileOutputStream fileOutputStream = new FileOutputStream(DiscordMinecraftMultiClient.whitelistFile);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8));

		try
		{
			for (String player : DiscordMinecraftMultiClient.whitelisted)
			{
				writer.write(player + '\n');
			}
		}
		catch (IOException e)
		{
			DiscordMinecraftMultiClient.log("Failed to write whitelist file");
		}
		finally
		{
			try
			{
				writer.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

}
