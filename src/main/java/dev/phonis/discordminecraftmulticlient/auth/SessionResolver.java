package dev.phonis.discordminecraftmulticlient.auth;

import java.util.concurrent.CompletableFuture;

public class SessionResolver extends CompletableFuture<SessionToken> {

    public final SessionToken sessionToken;
    public final String username;
    public final String password;

    public SessionResolver(SessionToken sessionToken, String username, String password) {
        this.sessionToken = sessionToken;
        this.username = username;
        this.password = password;
    }

}
