package activitystreamer.util;

public enum Field {
    COMMAND,
    USERNAME,
    SECRET,
    INFO,
    ID,
    LOAD,
    HOSTNAME,
    PORT,
    ACTIVITY,
    AUTHENTICATED_USER,
	LEVEL;
    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
