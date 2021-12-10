package dev.phonis.cosmicafkclient.util;

import dev.phonis.cosmicafkclient.client.McClient;

@FunctionalInterface
public interface NameChangeHandler extends TriConsumer<McClient, String, String> {

    @Override
    default void accept(McClient client, String to, String from) {
        this.onNameChange(client, to, from);
    }

    void onNameChange(McClient client, String to, String from);

}
