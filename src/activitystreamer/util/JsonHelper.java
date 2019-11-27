package activitystreamer.util;

import org.json.simple.JSONObject;

public class JsonHelper {
    private static Object get(JSONObject msg, Field key) {
        Object value = msg.get(key.toString());
        if (value == null) {
            throw new NullPointerException("The received message did not contain a " + key);
        }
        return value;
    }

    public static String getString(JSONObject msg, Field key) {
        return get(msg, key).toString();
    }

    public static int getInt(JSONObject msg, Field key) {
        return Integer.valueOf(getString(msg, key));
    }
}
