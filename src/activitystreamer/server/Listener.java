package activitystreamer.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;

public class Listener extends Thread{
	private static final Logger log = LogManager.getLogger(Listener.class);
	private ServerSocket serverSocket=null;
	private boolean term = false;
	private int portnum;
	
	public Listener() throws IOException{
		portnum = Settings.getLocalPort(); // keep our own copy in case it changes later
		serverSocket = new ServerSocket(portnum);//在本地开启一个serverSocket
		start();
	}
	
	@Override
	public void run() {
		log.info("listening for new connections on "+portnum);
		while(!term){
			Socket clientSocket;
			try {
				clientSocket = serverSocket.accept(); //如果收到了来自别的服务器的请求，转交控制权给clientSocket侦听
				//每收到一个连接请求，就建立一个新的入连接并添加进已连接list。
				Control.getInstance().incomingConnection(clientSocket);
			} catch (IOException e) {
				log.info("received exception, shutting down");
				term=true;
			}
		}
	}

	public void setTerm(boolean term) { //设置term参数，如果term被设置为true，就切断监听。
		this.term = term;
		if(term) interrupt();
	}
	
	
}
