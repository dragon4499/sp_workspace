package com.lgcns.test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

public class RunManager {
	static Map<String, MessageQueue> messageQueueMap;
	static Queue<String> consumerQueue;

	public static void main(String[] args) throws Exception {
		messageQueueMap = new HashMap<>();
		consumerQueue = new ConcurrentLinkedQueue<>();

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
	private Queue<Message> queue;
	private Queue<Message> dlq;
	private Map<String, Message> pendingMap;

	private String name;
	private int size;
	private int processTimeout;
	private int maxFailCount;
	private int waitTime;

	public MessageQueue(String name, int size, int processTimeout, int maxFailCount, int waitTime) {
		this.queue = new ConcurrentLinkedQueue<>();
		this.pendingMap = new ConcurrentHashMap<>();
		this.dlq = new ConcurrentLinkedQueue<>();

		this.name = name;
		this.size = size;
		this.processTimeout = processTimeout;
		this.maxFailCount = maxFailCount;
		this.waitTime = waitTime;

		// Thread queueUpdateThread = new Thread(new Runnable() {
		// 	@Override
		// 	public void run() {
		// 		update();
		// 		System.out.println(String.format("Queue [%s] is updated!", name));
		// 	}
		// });
		// queueUpdateThread.setDaemon(true);
		// queueUpdateThread.start();
		// try {
		// 	queueUpdateThread.join();
		// } catch (InterruptedException e) {
		// 	e.printStackTrace();
		// }
	}

	public boolean isFull() {
		return queue.size() >= size; 
	}

	public int getProcessTimeout() {
		return processTimeout;
	}

	public int getMaxFailCount() {
		return maxFailCount;
	}

	public int getWaitTime() {
		return waitTime;
	}

	public synchronized boolean offer(String msg) {
		return queue.offer(new Message(msg));
	}

	public synchronized Message poll() {
		Message pollMsg = null;

		for(Message msg : queue) {
			if(!pendingMap.containsKey(msg.getId())) {
				pollMsg = msg;
				pollMsg.setReceiveTime(new Date().getTime());
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
		if(pendingMap.containsKey(id)) {			
			pendingMap.remove(id);

			Message failMsg = null;
			for(Message msg : queue) {
				if(msg.getId().equals(id)) {
					failMsg = msg;					
					break;
				}
			}

			if(failMsg != null) {
				failMsg.addFailCount();
				failMsg.setReceiveTime(0L);

				if(failMsg.getFailCount() > maxFailCount) {
					offerDlq(failMsg);
				}
			}

			return true;
		} else {
			return false;
		}
	}

	private void offerDlq(Message msg) {
		dlq.offer(msg);
		queue.remove(msg);
	}

	public Message pollDlq() {
		return dlq.poll();
	}

	public void update() {
		for(Message msg : queue) {
			if(processTimeout > 0 && pendingMap.containsKey(msg.getId())) {
				long pendingTime = new Date().getTime() - msg.getReceiveTime();
			
				if(msg.getReceiveTime() > 0L && pendingTime >= processTimeout*1000L) {
					// rollback message
					pendingMap.remove(msg.getId());
					msg.setReceiveTime(0L);
					msg.addFailCount();

					System.out.println(String.format("[%s] Message rollback : %s [FC:%d]", name, msg.getMsg(), msg.getFailCount()));
				}
			}

			if(msg.getFailCount() > maxFailCount) {
				offerDlq(msg);
			}
		}
	}
}

class Message {
	private String id;
	private String msg;
	private int failCount;
	private long receiveTime;

	public Message(String msg) {
		this.id = UUID.randomUUID().toString();
		this.msg = msg;
		this.failCount = 0;
		this.receiveTime = 0L;
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

	public int getFailCount() {
		return failCount;
	}

	public void addFailCount() {
		this.failCount++;
	}

	public long getReceiveTime() {
		return receiveTime;
	}

	public void setReceiveTime(long receiveTime) {
		this.receiveTime = receiveTime;
	}
}