package dev.phonis.cosmicafkclient.util;

import java.util.Objects;

@FunctionalInterface
public interface TriConsumer<L, M, R> {

    void accept(L l, M m, R r);

    default TriConsumer<L, M, R> andThen(TriConsumer<? super L, ? super M, ? super R> after) {
        Objects.requireNonNull(after);

        return (l, m, r) -> {
            accept(l, m, r);
            after.accept(l, m, r);
        };
    }

}
