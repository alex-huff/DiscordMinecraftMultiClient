package dev.phonis.discordminecraftmulticlient.util;

@FunctionalInterface
public interface ThrowableConsumer<T, E extends Throwable>
{

    void accept(T toConsume) throws E;

}
