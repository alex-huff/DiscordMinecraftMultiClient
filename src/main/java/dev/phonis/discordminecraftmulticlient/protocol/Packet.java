package dev.phonis.discordminecraftmulticlient.protocol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Packet {

    public final int id;
    public final InputStream inputStream;

    public Packet(byte[] full) throws IOException {
        InputStream inputStream = new ByteArrayInputStream(full);
        this.id = DataTypes.getVarInt(inputStream);
        this.inputStream = inputStream;
    }

    public Packet(int id, byte[] data) {
        this.id = id;
        this.inputStream = new ByteArrayInputStream(data);
    }

}
