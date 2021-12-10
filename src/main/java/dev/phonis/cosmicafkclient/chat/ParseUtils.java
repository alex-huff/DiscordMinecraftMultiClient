package dev.phonis.cosmicafkclient.chat;

import com.google.gson.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseUtils {

    private static final Pattern tpPattern = Pattern.compile(
        "^\\[/TPA(HERE)?]\\s([a-zA-Z0-9_]*)\\s\\(.*\\)\\s(has\\srequested\\sto\\steleport\\sto\\syou.|has\\srequested\\sthat\\syou\\steleport\\sto\\sthem.)$"
    );

    public static String getTPRequesterIfTP(String rawMessage) {
        if (rawMessage == null)
            return null;

        Matcher matcher = ParseUtils.tpPattern.matcher(rawMessage);

        return matcher.find() ? matcher.group(2) : null;
    }

    public static String getRawMessage(String json) {
        try {
            return ParseUtils.getRawMessage(JsonParser.parseString(json));
        } catch (RuntimeException re) {
            re.printStackTrace();
        }

        return null;
    }

    private static String getRawMessage(JsonElement chatElement) {
        StringBuilder builder = new StringBuilder();

        ParseUtils.buildRawMessage(chatElement, builder);

        return builder.toString();
    }

    private static void buildRawMessage(JsonElement chatElement, StringBuilder builder) {
        if (chatElement.isJsonPrimitive()) {
            JsonPrimitive primitive = chatElement.getAsJsonPrimitive();

            if (primitive.isString())
                builder.append(chatElement.getAsJsonPrimitive().getAsString());

            return;
        }

        if (chatElement.isJsonObject()) {
            JsonObject object = chatElement.getAsJsonObject();

            if (object.has("text"))
                ParseUtils.buildRawMessage(object.get("text"), builder);

            if (object.has("extra"))
                ParseUtils.buildRawMessage(object.get("extra"), builder);

            return;
        }

        JsonArray array = chatElement.getAsJsonArray();

        for (JsonElement element : array) {
            ParseUtils.buildRawMessage(element, builder);
        }
    }


}
