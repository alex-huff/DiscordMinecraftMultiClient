package dev.phonis.discordminecraftmulticlient.auth;

import dev.phonis.discordminecraftmulticlient.util.ExponentialBackoff;

public
class AuthenticationBackoff implements ExponentialBackoff
{

	private final long   baseTimeout    = 20000; // 20 seconds
	private final long   maxTimeout     = 6000000; // 100 minutes
	private final double multiplier     = 1.5d; // exponent base
	private       long   currentTimeout = this.baseTimeout;

	@Override
	public
	void onSuccess()
	{
		this.currentTimeout = baseTimeout;
	}

	@Override
	public
	void onFailure()
	{
		this.currentTimeout = Math.min((long) (this.currentTimeout * this.multiplier), this.maxTimeout);
	}

	@Override
	public
	long getWaitTime()
	{
		return this.currentTimeout;
	}

}
