package com.lgcns.test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

public class RunManager {
	static Map<String, MessageQueue> messageQueueMap;

	public static void main(String[] args) throws Exception {
		messageQueueMap = new ConcurrentHashMap<>();

		new RunManager().start();
	}

	public void start() throws Exception {
		Server server = new Server();
		ServerConnector httpServer = new ServerConnector(server);
		httpServer.setHost("127.0.0.1");	
		httpServer.setPort(8080);
		server.addConnector(httpServer);

		ServletHandler servletHandler = new ServletHandler();
		servletHandler.addServletWithMapping(MessageServlet.class, "/*");
		server.setHandler(servletHandler);

		server.start();
		server.join();
	}
}

class MessageQueue {
	private ConcurrentLinkedQueue<Message> queue;
	private ConcurrentHashMap<String, Message> pendingMap;
	private int size;

	public MessageQueue(int size) {
		this.queue = new ConcurrentLinkedQueue<>();
		this.pendingMap = new ConcurrentHashMap<>();
		this.size = size;
	}

	public boolean isFull() {
		return queue.size() >= size; 
	}

	public boolean offer(String msg) {
		return queue.offer(new Message(msg));
	}

	public Message poll() {
		Message pollMsg = null;

		for(Message msg : queue) {
			if(!pendingMap.containsKey(msg.getId())) {
				pollMsg = msg;
				break;
			}
		}

		if(pollMsg != null) {
			// mark as pending
			pendingMap.put(pollMsg.getId(), pollMsg);
		}		

		return pollMsg;
	}

	public boolean ack(String id) {
		Message ackMsg = pendingMap.getOrDefault(id, null);

		if(ackMsg != null) {
			pendingMap.remove(id);
			queue.remove(ackMsg);
			return true;
		} else {
			return false;
		}
	}

	public boolean fail(String id) {
		Message failMsg = pendingMap.getOrDefault(id, null);
		
		if(failMsg != null) {
			pendingMap.remove(id);
			return true;
		} else {
			return false;
		}
	}
}

class Message {
	private String id;
	private String msg;

	public Message(String msg) {
		this.id = UUID.randomUUID().toString();
		this.msg = msg;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}
}