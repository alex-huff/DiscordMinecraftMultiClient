package dev.phonis.discordminecraftmulticlient.discord;

import dev.phonis.discordminecraftmulticlient.DiscordMinecraftMultiClient;
import dev.phonis.discordminecraftmulticlient.client.McClient;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.internal.entities.EntityBuilder;

import javax.security.auth.login.LoginException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public
class DiscordManager extends ListenerAdapter
{

	private static List<GatewayIntent> intents;

	static
	{
		DiscordManager.intents = new ArrayList<>();

		DiscordManager.intents.add(GatewayIntent.GUILD_MESSAGES);
	}

	public  JDA            jda;
	private MessageChannel altChannel;

	public
	DiscordManager(String token) throws InterruptedException
	{
		while (true)
		{
			try
			{
				this.login(token);

				break;
			}
			catch (Throwable e)
			{
				Thread.sleep(5000);
			}
		}
	}

	private
	void login(String token) throws InterruptedException, LoginException
	{
		this.jda = JDABuilder.create(token, DiscordManager.intents)
							 .setActivity(EntityBuilder.createActivity(":)", null, Activity.ActivityType.DEFAULT))
							 .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.VOICE_STATE,
								 CacheFlag.EMOTE, CacheFlag.ONLINE_STATUS).build();

		this.jda.awaitReady();

		this.altChannel = this.jda.getGuilds().get(0).getTextChannelsByName("alts", true).get(0);

		this.jda.addEventListener(this);
	}

	private static
	MessageEmbed embed(String title, String description, List<MessageEmbed.Field> fields,
					   MessageEmbed.Thumbnail thumbnail)
	{
		return new MessageEmbed(null, title, description, EmbedType.RICH, null, 1238, thumbnail, null, null, null,
			new MessageEmbed.Footer("CosmicAFK",
				"https://ddragon.leagueoflegends.com/cdn/10.19.1/img/champion/Twitch.png", null), null, fields);
	}

	private static
	MessageEmbed embed(String title, String description, List<MessageEmbed.Field> fields, String iconURL, int width,
					   int height)
	{
		return DiscordManager.embed(title, description, fields,
			new MessageEmbed.Thumbnail(iconURL, null, width, height));
	}

	public
	void sendEmbed(String title, String message)
	{
		this.altChannel.sendMessage(DiscordManager.embed(title, message, null, null)).queue();
	}

	public
	void sendPlayerEmbed(String player, String title, String message)
	{
		this.altChannel
			.sendMessage(DiscordManager.embed(title, message, null, "https://mc-heads.net/body/" + player, 180, 432))
			.queue();
	}

	public
	void sendMessage(String message)
	{ //assume only in one guild, with one channel named alts
		this.altChannel.sendMessage(message).queue();
	}

	@Override
	public
	void onMessageReceived(MessageReceivedEvent event)
	{
		if (event.getAuthor().getIdLong() == this.jda.getSelfUser().getIdLong())
		{
			return;
		}

		if (!event.getChannel().equals(this.altChannel))
		{
			return;
		}

		String line = event.getMessage().getContentRaw();

		try
		{
			this.handleCommand(line);
		}
		catch (InterruptedException | FileNotFoundException e)
		{
			e.printStackTrace();
		}
	}

	private
	void handleCommand(String line) throws InterruptedException, FileNotFoundException
	{
		String[] commandArgs = line.split(" ");

		switch (commandArgs[0])
		{
			case "whitelist" ->
			{
				if (commandArgs.length < 2)
				{
					this.sendMessage("Usage: whitelist (player)");

					break;
				}

				this.sendPlayerEmbed(commandArgs[1], "Whitelist", "Whitelisting " + commandArgs[1]);
				DiscordMinecraftMultiClient.whitelisted.add(commandArgs[1]);
			}

			case "showme" ->
			{
				if (commandArgs.length < 2)
				{
					this.sendMessage("Usage: showme (username)");

					break;
				}

				String name = commandArgs[1];

				if (DiscordMinecraftMultiClient.multiClient.withClient(name, McClient::toggleOut))
				{
					this.sendPlayerEmbed(name, "Toggle output", "Toggling " + name + "'s output");
				}
				else
				{
					this.sendMessage("No client with this player name yet");
				}
			}

			case "start" ->
			{
				if (commandArgs.length < 2)
				{
					this.sendMessage("Usage: start (username)");

					break;
				}

				String name = commandArgs[1];

				if (DiscordMinecraftMultiClient.multiClient.withClient(name, McClient::startClient))
				{
					this.sendPlayerEmbed(name, "Starter", "Started " + name);
				}
				else
				{
					this.sendEmbed("Starter", "No client with this player name yet");
				}
			}

			case "stop" ->
			{
				if (commandArgs.length < 2)
				{
					this.sendMessage("Usage: stop (username)");

					break;
				}

				String name = commandArgs[1];

				if (DiscordMinecraftMultiClient.multiClient.withClientThrowable(name, McClient::stopClient))
				{
					this.sendPlayerEmbed(name, "Stopper", "Stopped " + name);
				}
				else
				{
					this.sendEmbed("Stopper", "No client with this player name yet");
				}
			}

			case "restartall" ->
			{
				DiscordMinecraftMultiClient.multiClient.restartClients();
				this.sendEmbed("Restarter", "Restarted all clients");
			}

			case "stopall" ->
			{
				DiscordMinecraftMultiClient.multiClient.stopClients();
				this.sendEmbed("Stopper", "Stopped all clients");
			}

			case "exit" ->
			{
				DiscordMinecraftMultiClient.multiClient.stopClients();
				DiscordMinecraftMultiClient.shutdown();
				this.jda.shutdown();
			}

			case "send" ->
			{
				if (commandArgs.length < 3)
				{
					this.sendMessage("Usage: send (sender) (message)");

					break;
				}

				String        name           = commandArgs[1];
				StringBuilder messageBuilder = new StringBuilder();

				for (int i = 2; i < commandArgs.length; i++)
				{
					messageBuilder.append(commandArgs[i]);
					if (i != commandArgs.length - 1)
					{
						messageBuilder.append(' ');
					}
				}

				String message = messageBuilder.toString();

				if (DiscordMinecraftMultiClient.multiClient.withClientThrowable(name,
					client -> client.queueMessage(message)))
				{
					this.sendPlayerEmbed(name, "Sender", "Sending '" + message + "' as " + name);
				}
				else
				{
					this.sendMessage("No client with this player name yet");
				}
			}

			case "sendall" ->
			{
				if (commandArgs.length < 2)
				{
					this.sendMessage("Usage: sendall (message)");

					break;
				}

				StringBuilder messageBuilder = new StringBuilder();

				for (int i = 1; i < commandArgs.length; i++)
				{
					messageBuilder.append(commandArgs[i]);
					if (i != commandArgs.length - 1)
					{
						messageBuilder.append(' ');
					}
				}

				String message = messageBuilder.toString();

				DiscordMinecraftMultiClient.multiClient.forAllClientsThrowable(client -> client.queueMessage(message));
				this.sendEmbed("Sender", "Sent '" + message + "' from all players");
			}

			default -> this.sendMessage("Unknown command");
		}
	}

}
