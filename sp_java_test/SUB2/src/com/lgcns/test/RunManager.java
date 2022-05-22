package com.lgcns.test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;

public class RunManager {
	static Map<String, MessageQueue> messageQueueMap;

	public static void main(String[] args) {
		messageQueueMap = new HashMap<>();

		Scanner sc = new Scanner(System.in);

		String queueName, queueSize, msg;

		while(sc.hasNextLine()) {
			String input = sc.nextLine();
			String[] command = input.split(" ");

			switch(command[0]) {
				case "CREATE":
					queueName = command[1];
					queueSize = command[2];
				
					if(messageQueueMap.containsKey(queueName)) {
						System.out.println("Queue Exist");
					} else {
						// New queue added
						messageQueueMap.put(queueName, new MessageQueue(Integer.parseInt(queueSize)));
					}

					break;
				case "SEND":
					queueName = command[1];
					msg = command[2];

					if(messageQueueMap.containsKey(queueName)) {
						MessageQueue mq = messageQueueMap.get(queueName);

						if(mq.isFull()) {
							System.out.println("Queue Full");							
						} else {
							mq.offer(msg);
						}
					}

					break;
				case "RECEIVE":
					queueName = command[1];
					
					if(messageQueueMap.containsKey(queueName)) {
						MessageQueue mq = messageQueueMap.get(queueName);

						msg = mq.poll();
					} else {
						msg = null;
					}
					
					System.out.println(msg);				
					break;
				default:
					break;
			}
		}

		sc.close();
	}

}

class MessageQueue {
	private Queue<String> queue;
	private int size;

	public MessageQueue(int size) {
		this.queue = new LinkedList<>();
		this.size = size;
	}

	public boolean isFull() {
		return queue.size() >= size; 
	}

	public void offer(String msg) {
		queue.offer(msg);
	}

	public String poll() {
		return queue.poll();
	}
}