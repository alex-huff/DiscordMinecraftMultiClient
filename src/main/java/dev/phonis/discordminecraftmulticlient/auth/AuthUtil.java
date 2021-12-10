package dev.phonis.discordminecraftmulticlient.auth;

import fr.litarvan.openauth.AuthPoints;
import fr.litarvan.openauth.AuthenticationException;
import fr.litarvan.openauth.Authenticator;
import fr.litarvan.openauth.model.AuthAgent;
import fr.litarvan.openauth.model.response.AuthResponse;
import fr.litarvan.openauth.model.response.RefreshResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthUtil {

    private static final Authenticator authenticator = new Authenticator(Authenticator.MOJANG_AUTH_URL, AuthPoints.NORMAL_AUTH_POINTS);

    public static SessionToken authenticate(String username, String password) throws AuthenticationException {
        SessionToken token = new SessionToken();
        AuthResponse authResponse = AuthUtil.authenticator.authenticate(AuthAgent.MINECRAFT, username, password, token.clientID);
        token.id = authResponse.getAccessToken();
        token.playerName = authResponse.getSelectedProfile().getName();
        token.playerID = authResponse.getSelectedProfile().getId();

        return token;
    }

    public static SessionToken refresh(SessionToken sessionToken) throws AuthenticationException {
        SessionToken token = new SessionToken();
        RefreshResponse refreshResponse = AuthUtil.authenticator.refresh(sessionToken.id, sessionToken.clientID);
        token.id = refreshResponse.getAccessToken();
        token.playerName = refreshResponse.getSelectedProfile().getName();
        token.playerID = refreshResponse.getSelectedProfile().getId();

        return token;
    }

    public static String getServerHash(String serverID, byte[] publicKey, byte[] secretKey) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");

        md.update(serverID.getBytes(StandardCharsets.ISO_8859_1));
        md.update(secretKey);
        md.update(publicKey);

        return new BigInteger(md.digest()).toString(16);
    }

    public static boolean sessionCheck(String serverHash, String uuid, String accessToken) throws IOException {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/join");
        URLConnection urlConnection = url.openConnection();
        HttpURLConnection http = (HttpURLConnection) urlConnection;

        http.setRequestMethod("POST");
        http.setDoOutput(true);

        String jsonRequest = "{\"accessToken\":\"" + accessToken + "\",\"selectedProfile\":\"" + uuid + "\",\"serverId\":\"" + serverHash + "\"}";
        byte[] jsonRequestBytes = jsonRequest.getBytes(StandardCharsets.UTF_8);
        int length = jsonRequestBytes.length;

        http.setFixedLengthStreamingMode(length);
        http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        http.connect();

        try (OutputStream outputStream = http.getOutputStream()) {
            outputStream.write(jsonRequestBytes);
        }

        int code = http.getResponseCode();

        http.disconnect();

        return code == 204;
    }

}
