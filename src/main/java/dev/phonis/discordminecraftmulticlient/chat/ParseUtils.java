package dev.phonis.discordminecraftmulticlient.chat;

import com.google.gson.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ParseUtils {

    private static final Map<String, String> translationRules = new TreeMap<>();

    static {
        ParseUtils.translationRules.put("chat.type.admin", "[%s: %s]");
        ParseUtils.translationRules.put("chat.type.announcement", "§d[%s] %s");
        ParseUtils.translationRules.put("chat.type.emote", " * %s %s");
        ParseUtils.translationRules.put("chat.type.text", "<%s> %s");
        ParseUtils.translationRules.put("multiplayer.player.joined", "§e%s joined the game.");
        ParseUtils.translationRules.put("multiplayer.player.left", "§e%s left the game.");
        ParseUtils.translationRules.put("commands.message.display.incoming", "§7%s whispers to you: %s");
        ParseUtils.translationRules.put("commands.message.display.outgoing", "§7You whisper to %s: %s");
    }

    public static String getRawMessage(String json) {
        try {
            return ParseUtils.getRawString(JsonParser.parseString(json));
        } catch (RuntimeException re) {
            re.printStackTrace();
        }

        return null;
    }

    private static String translateString(String ruleName, List<String> usingData) {
        if (ParseUtils.translationRules.containsKey(ruleName)) {
            int usingIdx = 0;
            String rule = ParseUtils.translationRules.get(ruleName);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < rule.length(); i++) {
                if (rule.charAt(i) == '%' && i + 1 < rule.length()) {
                    if (rule.charAt(i + 1) == 's' || rule.charAt(i + 1) == 'd') {
                        if (usingData.size() > usingIdx) {
                            result.append(usingData.get(usingIdx));

                            usingIdx++;
                            i += 1;

                            continue;
                        }
                    } else if (Character.isDigit(rule.charAt(i + 1))
                        && i + 3 < rule.length() && rule.charAt(i + 2) == '$'
                        && (rule.charAt(i + 3) == 's' || rule.charAt(i + 3) == 'd')) {
                        int specifiedIdx = rule.charAt(i + 1) - '1';

                        if (usingData.size() > specifiedIdx) {
                            result.append(usingData.get(specifiedIdx));

                            usingIdx++;
                            i += 3;

                            continue;
                        }
                    }
                }

                result.append(rule.charAt(i));
            }

            return result.toString();
        } else {
            return "[" + ruleName + "] " + String.join(" ", usingData);
        }
    }

    private static String getRawString(JsonElement chatElement) {
        StringBuilder builder = new StringBuilder();

        ParseUtils.buildRawString(chatElement, builder);

        return builder.toString();
    }

    private static void buildRawString(JsonElement chatElement, StringBuilder builder) {
        if (chatElement.isJsonPrimitive()) {
            JsonPrimitive primitive = chatElement.getAsJsonPrimitive();

            if (primitive.isString())
                builder.append(chatElement.getAsJsonPrimitive().getAsString());

            return;
        }

        if (chatElement.isJsonObject()) {
            JsonObject object = chatElement.getAsJsonObject();

            if (object.has("text"))
                ParseUtils.buildRawString(object.get("text"), builder);

            if (object.has("extra"))
                ParseUtils.buildRawString(object.get("extra"), builder);

            if (object.has("translate") && object.has("with")) {
                List<String> usingData = new ArrayList<>();

                JsonArray array = object.get("with").getAsJsonArray();

                for (JsonElement element : array) {
                    StringBuilder stringBuilder = new StringBuilder();

                    ParseUtils.buildRawString(element, stringBuilder);

                    usingData.add(stringBuilder.toString());
                }

                builder.append(ParseUtils.translateString(object.get("translate").getAsString(), usingData));
            }

            return;
        }

        JsonArray array = chatElement.getAsJsonArray();

        for (JsonElement element : array) {
            ParseUtils.buildRawString(element, builder);
        }
    }

}
