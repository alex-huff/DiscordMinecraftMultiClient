package dev.phonis.discordminecraftmulticlient.util;

import dev.phonis.discordminecraftmulticlient.client.McClient;

@FunctionalInterface
public
interface NameChangeHandler extends TriConsumer<McClient, String, String>
{

	@Override
	default
	void accept(McClient client, String to, String from)
	{
		this.onNameChange(client, to, from);
	}

	void onNameChange(McClient client, String to, String from);

}
