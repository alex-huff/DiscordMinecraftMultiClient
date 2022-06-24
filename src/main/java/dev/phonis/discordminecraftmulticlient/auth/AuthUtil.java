package dev.phonis.discordminecraftmulticlient.auth;

import fr.litarvan.openauth.microsoft.MicrosoftAuthResult;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticationException;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticator;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public
class AuthUtil
{

	private static final MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();

	public static
	SessionToken authenticate(String username, String password) throws MicrosoftAuthenticationException
	{
		SessionToken        token        = new SessionToken();
		MicrosoftAuthResult authResponse = AuthUtil.authenticator.loginWithCredentials(username, password);
		token.id           = authResponse.getAccessToken();
		token.playerName   = authResponse.getProfile().getName();
		token.playerID     = authResponse.getProfile().getId();
		token.refreshToken = authResponse.getRefreshToken();

		return token;
	}

	public static
	SessionToken refresh(SessionToken sessionToken) throws MicrosoftAuthenticationException
	{
		SessionToken        newToken        = new SessionToken();
		MicrosoftAuthResult refreshResponse = AuthUtil.authenticator.loginWithRefreshToken(sessionToken.refreshToken);
		newToken.id           = refreshResponse.getAccessToken();
		newToken.playerName   = refreshResponse.getProfile().getName();
		newToken.playerID     = refreshResponse.getProfile().getId();
		newToken.refreshToken = refreshResponse.getRefreshToken();

		return newToken;
	}

	public static
	String getServerHash(String serverID, byte[] publicKey, byte[] secretKey) throws NoSuchAlgorithmException
	{
		MessageDigest md = MessageDigest.getInstance("SHA-1");

		md.update(serverID.getBytes(StandardCharsets.ISO_8859_1));
		md.update(secretKey);
		md.update(publicKey);

		return new BigInteger(md.digest()).toString(16);
	}

	public static
	boolean sessionCheck(String serverHash, String uuid, String accessToken) throws IOException
	{
		URL               url           = new URL("https://sessionserver.mojang.com/session/minecraft/join");
		URLConnection     urlConnection = url.openConnection();
		HttpURLConnection http          = (HttpURLConnection) urlConnection;

		http.setRequestMethod("POST");
		http.setDoOutput(true);

		String jsonRequest = "{\"accessToken\":\"" + accessToken + "\",\"selectedProfile\":\"" + uuid +
							 "\",\"serverId\":\"" + serverHash + "\"}";
		byte[] jsonRequestBytes = jsonRequest.getBytes(StandardCharsets.UTF_8);
		int    length           = jsonRequestBytes.length;

		http.setFixedLengthStreamingMode(length);
		http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		http.connect();

		try (OutputStream outputStream = http.getOutputStream())
		{
			outputStream.write(jsonRequestBytes);
		}

		int code = http.getResponseCode();

		http.disconnect();

		return code == 204;
	}

}
