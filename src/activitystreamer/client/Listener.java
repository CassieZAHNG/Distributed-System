package activitystreamer.client;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.net.SocketException;
public class Listener extends Thread {

	private BufferedReader reader;
	
	public Listener(BufferedReader reader) {
		this.reader = reader;
	}
	
	@Override
	public void run() {
		JSONParser parser = new JSONParser();
		try {
			String msg = null;
			//Read messages from the server while the end of the stream is not reached
			while((msg = reader.readLine()) != null) {
				JSONObject receiveJSONobj = (JSONObject) parser.parse(msg);
				String command = (String) receiveJSONobj.get("command");
				
				if (command.equals("REDIRECT") ){
					String hostname = (String) receiveJSONobj.get("hostname");
					
					String port = receiveJSONobj.get("port").toString();
					int remoteport = Integer.valueOf(port);
					
					Settings.setRemoteHostname(hostname);
					Settings.setRemotePort(remoteport);

					ClientSkeleton.getInstance().run();
				}

				ClientSkeleton.getInstance().readActivityObject(receiveJSONobj);
			}
		} catch (SocketException e) {
			System.out.println("Socket closed because the user typed exit");
		} catch (Exception e) {
			e.printStackTrace();
		} 	
	}
}
