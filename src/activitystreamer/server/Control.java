package activitystreamer.server;

import activitystreamer.util.Command;
import activitystreamer.util.Field;
import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static activitystreamer.util.Command.getCommand;
import static activitystreamer.util.JsonHelper.getInt;
import static activitystreamer.util.JsonHelper.getString;

@SuppressWarnings("unchecked")
public class Control extends Thread {
    private static final Logger log = LogManager.getLogger(Control.class);
    private static ArrayList<Connection> connections;
    private static HashMap<JSONObject, Long> statesMap;
    /**
     * There is no "anonymous" account in "registerInfo" and "loggedInfo"
     */
    private static JSONObject registerInfo;
    private static HashMap<Connection, String> loggedInfo;
    private static boolean term = false;
    private static Listener listener;
    private static final JSONParser parser = new JSONParser();
    private static final String id = Settings.nextSecret();
//yuan
    private int level = 0;
//yuan
    /**
     * Store the username and its client.
     */
    private static HashMap<String, Connection> reqClients;
    /**
     * Store the username and the servers who sent the lck_Request.
     */
    private static HashMap<String, Connection> reqServers;
    /**
     * Store the allowed count of the username
     */
    private static HashMap<String, Integer> allowedMap;
    /**
     * Choose the reply mode for Lock_Request, etc.
     */
    private static replyMode rMode;
    
    private Connection outgoingCon;

    protected static Control control = null;

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    public Control() {
        // initialize the arrays
        connections = new ArrayList<>();
        statesMap = new HashMap<>();
        registerInfo = new JSONObject();
        loggedInfo = new HashMap<>();
        reqClients = new HashMap<>();
        reqServers = new HashMap<>();
        allowedMap = new HashMap<>();
        rMode = replyMode.BROADCAST;
        // start a listener
        try {
            listener = new Listener();
        } catch (IOException e) {
            log.fatal("failed to startup a listening thread: " + e);
            System.exit(-1);
        }
        initiateConnection();
//        registerInfo.put("admin", "admin");//TODO: for login test
//        //只在3782上有这个注册信息
//        if (Settings.getLocalPort() == 3782) {
//            registerInfo.put("user", "admin");//TODO: for denied test
//        }
//        registerInfo.put("user1", "admin");//TODO: for redirect test
        start();
    }

    // public methods


    public void initiateConnection() {
        // make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteHostname() != null) {
        	                                        //yuan
        	
            try {
                outgoingCon = outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
                JSONObject jObj = new JSONObject();
                jObj.put(Field.COMMAND, Command.AUTHENTICATE.toString());
                jObj.put(Field.SECRET, Settings.getSecret());
                outgoingCon.writeMsg(jObj.toJSONString());
            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":"
                        + Settings.getRemotePort() + " :" + e);
//                System.exit(-1);// TODO:reserve
            }
        }
    }

    /*
     * A new incoming connection has been established, and a reference is
     * returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incoming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        connections.add(c);
        return c;

    }

    /*
     * A new outgoing connection has been established, and a reference is
     * returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        c.setServer(true);
        connections.add(c);
        return c;
    }


    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con) {
        if (!term)
            connections.remove(con);
    }

    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        int round = 0;
        while (!term) {
            // do something with 5 second intervals in between
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if (!term) {
//                log.debug("doing activity");
                term = doActivity();
//yuan
                if(round ==4){      //yuan 20s 之后去重连
                	if(serverRedirect(round)){ //如果超过20秒父节点在statesMap里面没更新 即需要重连
                		JSONObject targetserver = new JSONObject();
                		targetserver = getReconnectedServer();               		
                		String ip = (String) targetserver.get("hostname");
//                		String portStr = (String) targetserver.get("port");
                    	int port= Integer.parseInt((String) targetserver.get("port"));
                		Settings.setRemoteHostname(ip);
                		Settings.setRemotePort(port);
                		initiateConnection();
                    	round = 0;
                	}
                }
//yuan
                if (round == 5) {   //yuan 25s 之后清除list
                    statesCleaning(round);
                    round = 0;
                } 
                else round++;
            }

        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);//TODO
    }

    public boolean doActivity() {
        JSONObject state = new JSONObject();
        state.put(Field.COMMAND, Command.SERVER_ANNOUNCE.toString());
        state.put(Field.ID, id);
        state.put(Field.LOAD, getLoad());
        state.put(Field.HOSTNAME, Settings.getLocalHostname());
        state.put(Field.PORT, Settings.getLocalPort());
        state.put(Field.LEVEL,level);  //yuan
        broadcast(null, state, true);
        return false;
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public final ArrayList<Connection> getConnections() {
        return connections;
    }

    /*
     * Processing incoming messages from the connection. Return true if the
     * connection should close.
     */
    public synchronized boolean process(Connection con, String msg) {
        String errorMsg;
        try {
            JSONObject json = (JSONObject) parser.parse(msg);
            Command cmd = getCommand(json);
            if (!cmd.equals(Command.SERVER_ANNOUNCE)) {
                log.info("Message received: " + json);
            }//TODO:DELETE
            switch (cmd) {
                case AUTHENTICATE:
                    con.setServer(true);
                    return !procAuthenticate(con, json);
                case SERVER_ANNOUNCE:
                    return !procServerAnnc(con, json);
                case ACTIVITY_BROADCAST:
                    return !procActBrd(con, json);
                case LOCK_REQUEST:
                    return !procLckReq(con, json);
                case LOCK_DENIED:
                    procLckDenied(con, json);
                    break;
                case LOCK_ALLOWED:
                    procLckAllowed(con, json);
                    break;
                case REGISTER:
                    return !procRegister(con, json);
                case LOGIN:
                    return !procLogin(con, json);
                case LOGOUT:
                    loggedInfo.remove(con);
                    return true;
                case ACTIVITY_MESSAGE:
                    return !procActMsg(con, json);
//yuan
                case CLIENT_INFO:
                	return !proClientInfo(con, json);
//yuan
                case AUTHENTICATION_FAIL:
                case INVALID_MESSAGE:
                default:
                    return true;
            }
            return false;
        } catch (ParseException e) {
            errorMsg = String.format("JSON parse error while parsing message: %s", msg);

        } catch (Exception e) {//TODO:To be checked
            errorMsg = e.getMessage() + ":12345 " + msg; //4000断，5000报
        }
        con.reply(Command.INVALID_MESSAGE, errorMsg);
        return true;
    }


    // private methods

    /**
     * Write msg to all connections except incomingCon
     *
     * @param incomingCon incoming connection, can be null if no need to filter
     * @param msg         the message to be sent
     */
    private void broadcast(Connection incomingCon, JSONObject msg) {
        for (Connection con : connections) {
            if (con == incomingCon)
                continue;
            con.writeMsg(msg.toJSONString());
        }
    }

    private void broadcast(Connection incomingCon, JSONObject msg, boolean writeToServer) {
        for (Connection con : connections) {
            if (con == incomingCon)
                continue;
            boolean canWrite = writeToServer == con.isServer();
            if (canWrite) {
                con.writeMsg(msg.toJSONString());
            }
        }
    }

    private boolean procAuthenticate(Connection con, JSONObject msg) {
        String secret = getString(msg, Field.SECRET);
        if (secret.equals(Settings.getSecret())) {
            log.info("验证成功");
  //yuan
            JSONObject clientinfo = new JSONObject();
            clientinfo.put("command", "CLIENT_INFO");
            clientinfo.put("values", registerInfo);
            con.writeMsg(clientinfo.toJSONString());

  //yuan                
            return true;
        }
        con.reply(Command.AUTHENTICATION_FAIL, String.format("the supplied secret is incorrect: %s", secret));
        return false;
    }
    //yuan 
    private boolean proClientInfo(Connection con,JSONObject msg){
    	registerInfo = (JSONObject) msg.get("values");
    	return true;
    }
    //yuan 
    
    private boolean procLogin(Connection con, JSONObject msg) {
        String username = getString(msg, Field.USERNAME);
        loginStatus res = verifyUser(msg);
        switch (res) {

            case VALID_USER:
                if (!username.equals(Settings.ANONYMOUS)) {
                    loggedInfo.put(con, username);
                }
                con.reply(Command.LOGIN_SUCCESS, String.format("Logged in as user %s.", username));
                // Check if the client should be redirected.
                JSONObject state = getRedirectedServer();
                if (state != null) {
                    JSONObject red = new JSONObject();
                    red.put(Field.COMMAND, Command.REDIRECT.toString());
                    red.put(Field.HOSTNAME, getString(state, Field.HOSTNAME));
                    red.put(Field.PORT, getString(state, Field.PORT));
                    con.writeMsg(red.toJSONString());
                    return false;
                }
                return true;
            case HAS_LOGGED:
                con.reply(Command.LOGIN_FAILED, String.format("%s has logged.", username));
                return false;
            case INVALID_SECRET:
                con.reply(Command.LOGIN_FAILED, "attempt to login with wrong secret.");
                return false;
            case INVALID_USERNAME:
                con.reply(Command.LOGIN_FAILED, String.format("Invalid username: %s.", username));
                return false;
            default:
                return false;
        }
    }

    private boolean procActMsg(Connection con, JSONObject msg) throws ParseException {
        loginStatus res = verifyUser(msg);
        if (!res.equals(loginStatus.HAS_LOGGED)) {
            con.reply(Command.AUTHENTICATION_FAIL, "Invalid login information");
            return false;
        }
        // process the activity
        String username = getString(msg, Field.USERNAME);
        JSONObject activity = (JSONObject) parser.parse(getString(msg, Field.ACTIVITY));
        activity.put(Field.AUTHENTICATED_USER, username);
        // build the new json msg
        JSONObject brdMsg = new JSONObject();
        brdMsg.put(Field.COMMAND, Command.ACTIVITY_BROADCAST.toString());
        brdMsg.put(Field.ACTIVITY, activity);

        broadcast(null, brdMsg);
        return true;
    }
//yuan
    private boolean procServerAnnc(Connection con, JSONObject msg) {
        verifyServer(con);                             //yuan
         
        //rh.equals(ip)&&rp==port&&
        if(con!=null&&Settings.getRemoteHostname()!=null&&Settings.getLocalPort()!=Settings.getRemotePort()){  //TODO
            String ip= getString(msg,Field.HOSTNAME);
            String portStr = getString(msg,Field.PORT);
            int port= Integer.parseInt(portStr);
            
            String rh=Settings.getRemoteHostname();
            int rp = Settings.getRemotePort();

        	if(rh.equals(ip)&&rp==port&&con==outgoingCon){
            String secretStr = getString(msg, Field.LEVEL);  //有父节点的话
            int fatherLevel = Integer.parseInt(secretStr); 
        	if(level==0){                                    //初始化连接
        		level = fatherLevel+1;
        	}
        	if(level-fatherLevel>1 ||fatherLevel>=level){    //父节点断了，连到了爷爷节点，或者上层节点包括父节点的level都增加了
        		level = fatherLevel+1;                       //更新自己的level
            }
        	}
        }                                                     //yuan
        
       
        updateState(msg);
        broadcast(con, msg, true);
        return true;
    }
//yuan
    private void procLckDenied(Connection incomingCon, JSONObject msg) {
        switch (rMode) {

            case BROADCAST:
                procLckDenied1(incomingCon, msg);
                break;
            case P2P:
                procLckDenied2(incomingCon, msg);
                break;
        }
    }

    private void procLckDenied2(Connection incomingCon, JSONObject msg) {
        verifyServer(incomingCon);
        String username = getString(msg, Field.USERNAME);
        allowedMap.remove(username);
        // 0. The request comes from local client.
        if (reqClients.containsKey(username)) {
            Connection client = reqClients.get(username);
            client.reply(Command.REGISTER_FAILED, String.format("%s is already registered with the system", username));
            client.closeCon();
        }
        // 1. Remove the temp data.
        registerInfo.remove(username);
        // 2. broadcast the denied msg.
        broadcast(incomingCon, msg, true);
    }

    private void procLckAllowed(Connection incomingCon, JSONObject msg) {
        switch (rMode) {

            case BROADCAST:
                procLckAllowed1(incomingCon, msg);
                break;
            case P2P:
                procLckAllowed2(incomingCon, msg);
                break;
        }
    }

    private void procLckAllowed2(Connection incomingCon, JSONObject msg) {
        verifyServer(incomingCon);
        String username = getString(msg, Field.USERNAME);
        boolean denied = !allowedMap.containsKey(username);
        // 0. Check denied count first.
        if (denied) {
            return;
        }
        // 1. Check allowed count.
        int allowed = allowedMap.get(username);
        int need = getServerCount() - 1;
        if (need <= ++allowed) {
            // 1.1. The register request comes from other server's clients.
            if (!reqClients.containsKey(username)) {
                Connection lckReqServer = reqServers.get(username);
                lckReqServer.writeMsg(msg.toJSONString());
                allowedMap.remove(username);
                reqServers.remove(username);
                return;
            }
            // 1.2. The request comes from the client connecting to this server.
            Connection client = reqClients.get(username);
            client.reply(Command.REGISTER_SUCCESS, String.format("register success for %s", username));
            reqClients.remove(username);
            allowedMap.remove(username);
        } else {
            // 1.3. Update the allowed count.
            allowedMap.put(username, allowed);
        }
    }

    private boolean procLckReq(Connection incomingCon, JSONObject msg) {
        switch (rMode) {

            case BROADCAST:
                return procLckReq1(incomingCon, msg);
            case P2P:
                return procLckReq2(incomingCon, msg);
        }
        return false;
    }

    private boolean procLckReq2(Connection incomingCon, JSONObject msg) {
        // 0. verify if the incoming connection is authenticated.
        verifyServer(incomingCon);
        String username = getString(msg, Field.USERNAME);
        String secret = getString(msg, Field.SECRET);
        // 1. Check local storage first
        if (registerInfo.containsKey(username)) {
            JSONObject denied = getLockMsg(Command.LOCK_DENIED, username, secret);
            incomingCon.writeMsg(denied.toJSONString());
            return true;
        }
        // 2. Store the msg in local storage
        registerInfo.put(username, secret);
        // 3. Broadcast to other servers
        if (getServerCount() <= 1) {
            JSONObject lckReq = getLockMsg(Command.LOCK_ALLOWED, username, secret);
            incomingCon.writeMsg(lckReq.toJSONString());
            return true;
        }
        // 4. Start to sum up the allowed count.
        allowedMap.put(username, 0);
        // 5. Store the incoming server to reply it later.
        reqServers.put(username, incomingCon);
        // 6. Broadcast to other server.
        broadcast(incomingCon, msg, true);
        return true;
    }

    private boolean procRegister(Connection incomingCon, JSONObject msg) {
        String username = getString(msg, Field.USERNAME);
        String secret = getString(msg, Field.SECRET);
        loginStatus status = verifyUser(msg);
        if (status.equals(loginStatus.HAS_LOGGED)) {
            throw new IllegalArgumentException(String.format("The %s has been logged.", username));
        }
        if (registerInfo.containsKey(username)) {
            incomingCon.reply(Command.REGISTER_FAILED, String.format("%s is already registered with the system", username));
            return false;
        }
        // 2. Store the msg in local storage
        registerInfo.put(username, secret);
        if (getServerCount() == 0) {
            incomingCon.reply(Command.REGISTER_SUCCESS, String.format("register success for %s", username));
            return true;
        }
        reqClients.put(username, incomingCon);// stored the register clients
        allowedMap.put(username, 0);
        // 3. Send lock_request and lock_allowed
        JSONObject lckReq = getLockMsg(Command.LOCK_REQUEST, username, secret);
        broadcast(null, lckReq, true);
        return true;
    }

    private void procLckDenied1(Connection incomingCon, JSONObject msg) {
        verifyServer(incomingCon);
        String username = getString(msg, Field.USERNAME);
        // 0. broadcast the denied msg.
        broadcast(incomingCon, msg, true);
        // 1. There is no need to process this request.
        if (!reqClients.containsKey(username)) {
            return;
        }
        // 2. Remove the temp data.
        registerInfo.remove(username);
        allowedMap.remove(username);
        // 3. The request comes from local client.
        Connection client = reqClients.get(username);
        client.reply(Command.REGISTER_FAILED, String.format("%s is already registered with the system", username));
        client.closeCon();
    }

    private void procLckAllowed1(Connection incomingCon, JSONObject msg) {
        verifyServer(incomingCon);
        String username = getString(msg, Field.USERNAME);
        // 0. broadcast the allowed msg.
        broadcast(incomingCon, msg, true);
        // 1. There is no need to process this request.
        if (!reqClients.containsKey(username)) {
            return;
        }
        // 2. Check denied count first.
        boolean denied = !allowedMap.containsKey(username);
        if (denied) {
            return;
        }
        // 3. Sum up the count of allowed servers.
        int need = getServerCount();
        int allowed = allowedMap.get(username);
        // 4. Check allowed count.
        if (need <= ++allowed) {
            Connection client = reqClients.get(username);
            client.reply(Command.REGISTER_SUCCESS, String.format("register success for %s", username));
            reqClients.remove(username);
            allowedMap.remove(username);
        } else {
            allowedMap.put(username, allowed);
        }
    }

    private boolean procLckReq1(Connection incomingCon, JSONObject msg) {
        // 0. verify if the incoming connection is authenticated.
        verifyServer(incomingCon);
        String username = getString(msg, Field.USERNAME);
        String secret = getString(msg, Field.SECRET);
        // 1. Check local storage first
        if (registerInfo.containsKey(username)) {
            JSONObject denied = getLockMsg(Command.LOCK_DENIED, username, secret);
            incomingCon.writeMsg(denied.toJSONString());
            return true;
        }
        // 2. Store the msg in local storage
        registerInfo.put(username, secret);
        JSONObject allow = getLockMsg(Command.LOCK_ALLOWED, username, secret);
        incomingCon.writeMsg(allow.toJSONString());
        // 3. Broadcast to other server.
        broadcast(incomingCon, msg, true);
        return true;
    }

    private boolean procActBrd(Connection incomingCon, JSONObject msg) {
        verifyServer(incomingCon);
        broadcast(incomingCon, msg);
        return true;
    }

    private int getLoad() {
        int load = 0;
        for (Connection con : connections) {
            if (!con.isServer()) {
                load++;
            }
        }
        return load;
    }

    private JSONObject getRedirectedServer() {
        int localLoad = getLoad();
        for (JSONObject state : statesMap.keySet()) {
            int load = getInt(state, Field.LOAD);
            if (load < localLoad - 2) {
                return state;
            }
        }
        return null;
    }
      
//yuan
   private JSONObject getReconnectedServer() {      //返回重新连接的server的JSONObject
	   HashMap<JSONObject, Long> backUp = statesMap ;
	   List<Integer> potentialTarget = new ArrayList<Integer>();
	   
	   for (Map.Entry<JSONObject, Long> fatherSocket : backUp.entrySet()) {
		   String portStr = String.valueOf(Settings.getRemotePort());
           if (Settings.getRemoteHostname().equals(getString(fatherSocket.getKey(), Field.HOSTNAME))&&
        	  portStr.equals(getString(fatherSocket.getKey(), Field.PORT))   ) {
        	   backUp.remove(fatherSocket.getKey());
               break;
           }
       }
	      
	   for (JSONObject state : backUp.keySet()){
           String stateStr = getString(state, Field.LEVEL);
           int stateLevel = Integer.parseInt(stateStr);

		   if (stateLevel<level ){                  //从stateMap里面找出所有level比自己小的server
			   potentialTarget.add(stateLevel);          //level放到一个新的arraylist里面
		   }
	   }
	   if(potentialTarget.size()==0){
		   JSONObject backupServer = new JSONObject();
		   backupServer.put("hostname", Settings.getBackupHostname());
		   String portStr = String.valueOf(Settings.getBackupPort());
		   backupServer.put("port", portStr);
		   return backupServer;
	   }else{
		   int potentialLevel = Collections.max(potentialTarget); //目标server的level值
		   for(JSONObject state : backUp.keySet()){            // 遍历整个statesMap，找出一个与目标level值相等的server JSONObject
	           String stateStr = getString(state, Field.LEVEL);//有父节点的话
	           int stateLevel = Integer.parseInt(stateStr);
			   if (stateLevel == potentialLevel ){
				   JSONObject commonState = new JSONObject();
				   commonState.put("hostname",getString(state, Field.HOSTNAME) );
				   String portStr = getString(state, Field.PORT);
				   commonState.put("port", portStr);
				   return commonState;
			   }
			   break;
		   }
	   }
	   return null;
   }                                            
 //yuan
   
   
    // TODO: local server uninvolved
    private int getServerCount() {
        switch (rMode) {

            case BROADCAST:
                return statesMap.size();
            case P2P:
                int count = 0;
                for (Connection con : connections) {
                    if (con.isServer()) {
                        count++;
                    }
                }
                return count;
        }
        return -1;
    }

    private void updateState(JSONObject state) {
        boolean exist = false;
        long nano = System.nanoTime() / 1000000;
        for (Map.Entry<JSONObject, Long> pastState : statesMap.entrySet()) {
            if (getString(state, Field.ID).equals(getString(pastState.getKey(), Field.ID))) {
                exist = true;
                statesMap.remove(pastState.getKey());
                statesMap.put(state, nano);
//                log.info("信息更新: " + state);
                break;
            }
        }
        if (!exist) {
            statesMap.put(state, nano);
            log.info("增加条目: " + state + "current size: " + statesMap.size());
        }
    }

    /**
     * Removes the server states have not updated yet.
     *
     * @param rounds The rounds receiving the SERVER_ANNOUNCE.
     */
    private static void statesCleaning(int rounds) {
        long timeDiff = Settings.getActivityInterval() * rounds;
        long nano = System.nanoTime() / 1000000;
        JSONObject[] keys = new JSONObject[statesMap.keySet().size()];
        statesMap.keySet().toArray(keys);
        for (JSONObject key : keys) {
            long time = statesMap.get(key);
            if (time < nano - timeDiff) {
                statesMap.remove(key);
                log.info("移除：" + key);
            }          	
          }
        }
//yuan              首先判断是不是父节点 判断server在 n*5s 是否要重连 
    private static boolean serverRedirect(int rounds){
    	long timeDiff = Settings.getActivityInterval() * rounds;
    	long nano = System.nanoTime() / 1000000;
        JSONObject[] keys = new JSONObject[statesMap.keySet().size()];
        statesMap.keySet().toArray(keys);
        for (JSONObject key : keys) {
            long time = statesMap.get(key);
            if (time < nano - timeDiff) {
            	String ip=getString(key,Field.HOSTNAME);
            	String portStr=getString(key,Field.PORT);
            	int port=Integer.parseInt(portStr);
            	String rh=Settings.getRemoteHostname();
            	if(rh!=null&&rh.equals(ip)&&Settings.getRemotePort()==port){
                	return true;       		
            	}
            }          	
            }
        return false;
    }
//yuan
    /**
     * Returns if the msg contains valid user
     *
     * @param msg the message from client
     * @return VALID_USER  , if the user was verified;<p>
     * HAS_LOGGED       , if the user has logged;<p>
     * INVALID_SECRET   , if the username and secret do not match the logged in the user;<p>
     * INVALID_USERNAME , if the user has not registered yet;<p>
     */
    private static loginStatus verifyUser(JSONObject msg) {
        String username = getString(msg, Field.USERNAME);
        if (registerInfo.containsKey(username)) {
            String secret = getString(msg, Field.SECRET);
            if (!secret.equals(registerInfo.get(username))) {
                return loginStatus.INVALID_SECRET;
            }
        } else if (!username.equals(Settings.ANONYMOUS)) {
            return loginStatus.INVALID_USERNAME;
        }
        if (loggedInfo.containsValue(username)) {
            return loginStatus.HAS_LOGGED;
        }
        return loginStatus.VALID_USER;
    }

    private static void verifyServer(Connection con) {
        if (!con.isServer()) {
            throw new IllegalArgumentException("This server is unauthenticated.");
        }
    }

    private static JSONObject getLockMsg(Command cmd, String username, String secret) {
        JSONObject lckReq = new JSONObject();
        lckReq.put(Field.COMMAND, cmd.toString());
        lckReq.put(Field.USERNAME, username);
        lckReq.put(Field.SECRET, secret);
        return lckReq;
    }

    private enum loginStatus {
        VALID_USER,
        HAS_LOGGED,
        INVALID_SECRET,
        INVALID_USERNAME,
    }

    /**
     * The reply mode for lock_request, etc.
     */
    private enum replyMode {
        BROADCAST,
        P2P,
    }
}
