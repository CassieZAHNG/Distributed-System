package activitystreamer.server;


import activitystreamer.util.Command;
import activitystreamer.util.Field;
import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;

@SuppressWarnings("unchecked")
public class Connection extends Thread {
    private static final Logger log = LogManager.getLogger(Connection.class);
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inreader;
    private PrintWriter outwriter;
    private boolean open;
    private Socket socket;
    private boolean term = false;
    private boolean isServer = false;

    Connection(Socket socket) throws IOException {
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        inreader = new BufferedReader(new InputStreamReader(in));
        outwriter = new PrintWriter(out, true);
        this.socket = socket;
        open = true;
        start();
    }

    /*
     * returns true if the message was written, otherwise false
     */
    public boolean writeMsg(String msg) {
        if (open) {
            outwriter.println(msg);
            outwriter.flush();
            if (!msg.contains(Command.SERVER_ANNOUNCE.toString())) {
                log.info("Message sent: " + msg);
            }//TODO: DELETE
            return true;
        }
        return false;
    }

    /*
     * returns true if the error message was written, otherwise false
     */
    public boolean reply(Command cmd, String msg) {
        JSONObject jObj = new JSONObject();
        jObj.put(Field.COMMAND, cmd.toString());
        jObj.put(Field.INFO, msg);
        return writeMsg(jObj.toJSONString());
    }

    public void closeCon() {
        if (open) {
            log.info("closing connection " + Settings.socketAddress(socket));
            try {
                term = true;
                if (socket != null) {
                    socket.close();
                }
                inreader.close();
                out.close();
            } catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
            }
        }
    }


    public void run() {
        try {
            String msg;
            while (!term) {
                msg = inreader.readLine();
                term = Control.getInstance().process(this, msg);
            }
            log.debug("connection closed to " + Settings.socketAddress(socket));
            in.close();
        } catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);

        } finally {
            closeCon();
            Control.getInstance().connectionClosed(this);
        }
        open = false;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isServer() {
        return isServer;
    }

    public void setServer(boolean server) {
        isServer = server;
    }

    public boolean isOpen() {
        return open;
    }

}
