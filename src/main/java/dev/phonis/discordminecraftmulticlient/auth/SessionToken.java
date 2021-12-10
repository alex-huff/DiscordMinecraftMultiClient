package dev.phonis.discordminecraftmulticlient.auth;

import java.util.UUID;

public class SessionToken {

    public String id;
    public String playerName;
    public String playerID;
    public final String clientID;

    public SessionToken() {
        this.clientID = UUID.randomUUID().toString().replace("-", "");
    }

}
