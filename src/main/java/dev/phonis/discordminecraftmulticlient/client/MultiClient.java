package dev.phonis.discordminecraftmulticlient.client;

import dev.phonis.discordminecraftmulticlient.DiscordMinecraftMultiClient;
import dev.phonis.discordminecraftmulticlient.util.ThrowableConsumer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public
class MultiClient
{

	private final List<McClient>        clients   = new ArrayList<>();
	private final Map<String, McClient> clientMap = new TreeMap<>();
	private final Object                stateLock = new Object();

	public static
	MultiClient fromFile(File accountsFile)
	{
		MultiClient multiClient = new MultiClient();

		try
		{
			if (accountsFile.createNewFile())
			{
				DiscordMinecraftMultiClient.log("Creating accounts file");
			}

			FileInputStream fileInputStream = new FileInputStream(accountsFile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));
			String line;

			while ((line = reader.readLine()) != null)
			{
				if (line.startsWith("#"))
				{
					continue;
				}
				String[] params = line.split(" ");

				if (params.length != 4)
				{
					continue;
				}

				int port;

				try
				{
					port = Integer.parseInt(params[3]);
				}
				catch (NumberFormatException e)
				{
					e.printStackTrace();

					continue;
				}

				multiClient.addClient(params[0], params[1], params[2], port);
			}

			reader.close();
		}
		catch (IOException e)
		{
			DiscordMinecraftMultiClient.log("Failed to read " + accountsFile.getName());
		}

		return multiClient;
	}

	public
	void toFile(File accountsFile) throws FileNotFoundException
	{
		FileOutputStream fileOutputStream = new FileOutputStream(accountsFile);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8));

		try
		{
			synchronized (this.stateLock)
			{
				for (McClient client : this.clients)
				{
					writer.write(
						client.username + " " + client.password + " " + client.serverIP + " " + client.port + '\n');
				}
			}
		}
		catch (IOException e)
		{
			DiscordMinecraftMultiClient.log("Failed to write accounts file");
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

	private
	void addClient(String username, String password, String serverIP, int port)
	{
		McClient client = new McClient(username, password, serverIP, port, this::onNameChange);

		synchronized (this.stateLock)
		{
			this.clients.add(client);
		}
	}

	public synchronized
	void addAndStartClient(String username, String password, String serverIP, int port)
	{
		McClient client = new McClient(username, password, serverIP, port, this::onNameChange);

		synchronized (this.stateLock)
		{
			this.clients.add(client);
		}

		client.startClient();
	}

	private
	void onNameChange(McClient client, String to, String from)
	{
		synchronized (this.stateLock)
		{
			if (from != null)
			{
				this.clientMap.remove(from);
			}

			this.clientMap.put(to, client);
		}

		DiscordMinecraftMultiClient.log(client.username + "'s " + "name is: " + to);
	}

	public synchronized
	void startClients()
	{
		synchronized (this.stateLock)
		{
			this.clients.forEach(McClient::startClient);
		}

		DiscordMinecraftMultiClient.log("Started all clients");
	}

	public synchronized
	void stopClients() throws InterruptedException
	{
		List<McClient> toStop;

		synchronized (this.stateLock)
		{
			toStop = new ArrayList<>(this.clients);
		}

		for (McClient client : toStop)
		{
			client.stopClient();
		}

		DiscordMinecraftMultiClient.log("Stopped all clients");
	}

	public synchronized
	void restartClients() throws InterruptedException
	{
		List<McClient> toRestart;

		synchronized (this.stateLock)
		{
			toRestart = new ArrayList<>(this.clients);
		}

		for (McClient client : toRestart)
		{
			client.restartClient();
		}

		DiscordMinecraftMultiClient.log("Restarted all clients");
	}

	public synchronized
	boolean withClient(String name, Consumer<McClient> consumer)
	{
		McClient client;

		synchronized (this.stateLock)
		{
			client = this.clientMap.get(name);
		}

		if (client == null)
		{
			return false;
		}

		consumer.accept(client);

		return true;
	}

	public synchronized
	<T extends Throwable> boolean withClientThrowable(String name, ThrowableConsumer<McClient, T> consumer) throws T
	{
		McClient client;

		synchronized (this.stateLock)
		{
			client = this.clientMap.get(name);
		}

		if (client == null)
		{
			return false;
		}

		consumer.accept(client);

		return true;
	}

	public synchronized
	void forAllClients(Consumer<McClient> consumer)
	{
		List<McClient> toConsume;

		synchronized (this.stateLock)
		{
			toConsume = new ArrayList<>(this.clients);
		}

		toConsume.forEach(consumer);
	}

	public synchronized
	<T extends Throwable> void forAllClientsThrowable(ThrowableConsumer<McClient, T> consumer) throws T
	{
		List<McClient> toConsume;

		synchronized (this.stateLock)
		{
			toConsume = new ArrayList<>(this.clients);
		}

		for (McClient client : toConsume)
		{
			consumer.accept(client);
		}
	}

}
