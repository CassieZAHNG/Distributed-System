package activitystreamer.client;

import activitystreamer.util.Command;
import activitystreamer.util.Field;
import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger(ClientSkeleton.class);
	private static ClientSkeleton clientSolution;
	private TextFrame textFrame;
	private Socket socket;
	private BufferedWriter writer;
    private BufferedReader reader;
	private String receiveStr;
	
	public static ClientSkeleton getInstance(){
		if(clientSolution==null){
			clientSolution = new ClientSkeleton();
		}
		return clientSolution;
	}
	
	public ClientSkeleton(){
					
		textFrame = new TextFrame();
		start();

	}
	
	@SuppressWarnings("unchecked")
	public void sendActivityObject(JSONObject activityObj){
		try{
			JSONObject broadcastObj = new JSONObject();
			broadcastObj.put("command", "ACTIVITY_MESSAGE");
			broadcastObj.put("username",Settings.getUsername());
			broadcastObj.put("secret", Settings.getSecret());
			broadcastObj.put("activity", activityObj);
			String broadcastStr = broadcastObj.toJSONString();			
			writer.write(broadcastStr+"\n");
			writer.flush();
			
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	public void readActivityObject(JSONObject activityObj) throws IOException {
		String command = (String) activityObj.get("command");
		if(command.equals("REGISTER_SUCCESS")){
			JSONObject cmdObject = new JSONObject();
			cmdObject.put("command", "LOGIN");
			cmdObject.put("username", Settings.getUsername());
			cmdObject.put("secret", Settings.getSecret());
			writer.write(cmdObject.toJSONString()+"\n");
			writer.flush();
		}
		textFrame.setOutputText(activityObj);
	}
	
	public String ChangeCmd(String cmdUsername,String cmdSecret){ //according to the cmd input,changing them into different JSONObject,
		                                                          //and transform them into String to send to the server.
		String username = cmdUsername;
		String secret = cmdSecret;
		JSONObject cmdObject = new JSONObject();
		if(username.equals("anonymous")){
			cmdObject.put("command", "LOGIN");
			cmdObject.put("username", "anonymous");			
		}	
		
		if(!cmdUsername.equals("anonymous") && secret != null){
			cmdObject.put("command", "LOGIN");
			cmdObject.put("username", username);	
			cmdObject.put("secret", secret);
		}
		
		if(!cmdUsername.equals("anonymous") && secret == null){
			secret = Settings.nextSecret();
			Settings.setSecret(secret);
			cmdObject.put("command", "REGISTER");
			cmdObject.put("username", username);	
			cmdObject.put("secret", secret);	
		}
		String cmd = cmdObject.toJSONString();	
		return cmd;
	}
	
	public void disconnect(){		
		try {
			JSONObject logout = new JSONObject();
			logout.put(Field.COMMAND, Command.LOGOUT.toString());
			writer.write(logout.toJSONString() + "\n");
			writer.flush();
			Listener.interrupted();
			reader.close();
			writer.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	public void run(){    

		String username = Settings.getUsername();
		String secret = Settings.getSecret();
		try {
			socket = new Socket(Settings.getRemoteHostname(),Settings.getRemotePort());
			System.out.println("Connection established");
			this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
			this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));	
			Listener receiver = new Listener(reader);
			receiver.start();
			writer.write(ChangeCmd(username,secret) + "\n");
			writer.flush();				
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}	
}
