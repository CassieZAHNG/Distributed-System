package activitystreamer.util;

import org.json.simple.JSONObject;

import static activitystreamer.util.JsonHelper.getString;

public enum Command {
    // to server
    AUTHENTICATE,
    AUTHENTICATION_FAIL,
    SERVER_ANNOUNCE,
    ACTIVITY_BROADCAST,
    LOCK_REQUEST,
    LOCK_DENIED,
    LOCK_ALLOWED,

    // to client
    LOGIN_SUCCESS,
    REDIRECT,
    LOGIN_FAILED,
    REGISTER_FAILED,
    REGISTER_SUCCESS,

    // from client
    REGISTER,
    LOGIN,
    LOGOUT,
    ACTIVITY_MESSAGE,
    CLIENT_INFO,

    // others
    INVALID_MESSAGE;

    public static Command getCommand(JSONObject msg) {
        String cmd = getString(msg, Field.COMMAND);
        return Command.valueOf(cmd.toUpperCase());
    }
}
