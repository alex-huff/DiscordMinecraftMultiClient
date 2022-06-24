package dev.phonis.discordminecraftmulticlient.client;

import dev.phonis.discordminecraftmulticlient.DiscordMinecraftMultiClient;
import dev.phonis.discordminecraftmulticlient.util.Waiter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public
class LoginQueue
{

	private static final BlockingQueue<Waiter> loginQueue      = new LinkedBlockingQueue<>();
	public static final  Thread                restarterThread = new Thread(() ->
																			{
																				while (!Thread.currentThread()
																							  .isInterrupted())
																				{
																					try
																					{
																						LoginQueue.loginQueue.take()
																											 .wake();
																						Thread.sleep(10000);
																					}
																					catch (InterruptedException e)
																					{
																						break;
																					}
																				}

																				DiscordMinecraftMultiClient.log(
																					"Closing restarter thread");
																			});

	static
	{
		LoginQueue.restarterThread.start();
	}

	public static
	void waitForTurn() throws InterruptedException
	{
		Waiter waiter = new Waiter();

		LoginQueue.loginQueue.put(waiter);

		waiter.rest();
	}

}