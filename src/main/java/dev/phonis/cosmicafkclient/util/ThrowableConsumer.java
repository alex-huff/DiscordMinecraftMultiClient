package dev.phonis.cosmicafkclient.util;

@FunctionalInterface
public interface ThrowableConsumer<T, E extends Throwable> {

    void accept(T toConsume) throws E;

}
