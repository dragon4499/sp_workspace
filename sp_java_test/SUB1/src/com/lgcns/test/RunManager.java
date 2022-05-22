package com.lgcns.test;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class RunManager {
	public static void main(String[] args) {
		Queue<String> messageQueue = new LinkedList<>();

		Scanner sc = new Scanner(System.in);

		while(sc.hasNextLine()) {
			String input = sc.nextLine();
			String[] command = input.split(" ");

			switch(command[0]) {
				case "SEND":
					messageQueue.offer(command[1]);
					break;
				case "RECEIVE":
					String msg = messageQueue.poll();
					System.out.println(msg);				
					break;
				default:
					break;
			}
		}

		sc.close();
	}
}
