package dev.phonis.discordminecraftmulticlient.auth;

import dev.phonis.discordminecraftmulticlient.DiscordMinecraftMultiClient;
import dev.phonis.discordminecraftmulticlient.util.ExponentialBackoff;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticationException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

public
class Authenticator
{

	private static final BlockingQueue<SessionResolver> sessionQueue  = new LinkedBlockingQueue<>();
	public static final  Thread                         sessionThread = new Thread(() ->
	{
		ExponentialBackoff backoff = new AuthenticationBackoff();

		while (!Thread.currentThread().isInterrupted())
		{
			try
			{
				SessionResolver sessionResolver = Authenticator.sessionQueue.take();
				SessionToken    sessionToken    = null;

				while (sessionToken == null)
				{
					try
					{
						sessionToken = Authenticator.tryGetOrRefreshToken(sessionResolver);

						backoff.onSuccess(); // yay!
					}
					catch (MicrosoftAuthenticationException e)
					{
						DiscordMinecraftMultiClient.log(
							"Auth error on " + sessionResolver.username + ", backing off: " + backoff.getWaitTime());
						DiscordMinecraftMultiClient.log(e.getMessage());
						backoff.backoff(); // f
					}
				}

				sessionResolver.complete(sessionToken);
				// Wait 15 seconds in
				// between
				// authentications
				Thread.sleep(15000);
			}
			catch (InterruptedException e)
			{
				break;
			}
		}

		sessionQueue.forEach(sessionResolver -> sessionResolver.cancel(true));
		sessionQueue.clear();
		DiscordMinecraftMultiClient.log("Closing session thread");
	});

	static
	{
		Authenticator.sessionThread.start();
	}

	public static
	SessionToken getOrRefreshSession(SessionToken sessionToken, String username, String password)
		throws InterruptedException, ExecutionException
	{
		return Authenticator.getSessionResolver(sessionToken, username, password).get();
	}

	private static
	SessionResolver getSessionResolver(SessionToken sessionToken, String username, String password)
		throws InterruptedException
	{
		SessionResolver sessionResolver = new SessionResolver(sessionToken, username, password);

		Authenticator.sessionQueue.put(sessionResolver);

		return sessionResolver;
	}

	private static
	SessionToken tryGetOrRefreshToken(SessionResolver sessionResolver)
		throws InterruptedException, MicrosoftAuthenticationException
	{
		SessionToken sessionToken;

		Authenticator.checkInterrupted();

		if (sessionResolver.sessionToken == null)
		{
			sessionToken = AuthUtil.authenticate(sessionResolver.username, sessionResolver.password);
		}
		else
		{
			try
			{
				sessionToken = AuthUtil.refresh(sessionResolver.sessionToken);

				DiscordMinecraftMultiClient.log("Successfully refreshed session");
			}
			catch (MicrosoftAuthenticationException e)
			{
				Authenticator.checkInterrupted();
				DiscordMinecraftMultiClient.log("Failed to refresh session, creating new one");

				sessionToken = AuthUtil.authenticate(sessionResolver.username, sessionResolver.password);
			}
		}

		return sessionToken;
	}

	private static
	void checkInterrupted() throws InterruptedException
	{
		if (Thread.currentThread().isInterrupted())
		{
			throw new InterruptedException();
		}
	}
}
