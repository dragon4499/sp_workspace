package com.lgcns.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MessageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] requestUri = req.getRequestURI().split("/");

        // Execute command
        JsonObject responseBody = new JsonObject();

        String queueName = requestUri[2];

        Message msg = null;
        switch(requestUri[1]) {
            case "RECEIVE":                
                msg = receiveMessage(queueName);
                break;
            case "DLQ":
                msg = dlqMessage(queueName);
            default:
                break;
        }

        if(msg != null) {
            responseBody.addProperty("Result", "Ok");
            responseBody.addProperty("MessageID", msg.getId());
            responseBody.addProperty("Message", msg.getMsg());
        } else {
            responseBody.addProperty("Result", "No Message");
        }

        // Generate response 
        resp.setStatus(200);
        resp.getWriter().write(responseBody.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] requestUri = req.getRequestURI().split("/");
        
        // Parse request
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        try {
            br = new BufferedReader(new InputStreamReader(req.getInputStream()));
            char[] charBuffer = new char[128];
            int bytesRead = -1;
            while((bytesRead = br.read(charBuffer)) > 0) {
                sb.append(charBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if(br != null) {
                br.close();
            }
        } 

        JsonElement body = JsonParser.parseString(sb.toString());

        // Execute command
        JsonObject responseBody = new JsonObject();
    
        String queueName = requestUri[2];

        switch(requestUri[1]) {
            case "CREATE":                
                int queueSize = body.getAsJsonObject().get("QueueSize").getAsInt();
                int processTimeout = body.getAsJsonObject().get("ProcessTimeout").getAsInt();
                int maxFailCount = body.getAsJsonObject().get("MaxFailCount").getAsInt();
                int waitTime = body.getAsJsonObject().get("WaitTime").getAsInt();

                boolean isCreated = createQueue(queueName, queueSize, processTimeout, maxFailCount, waitTime);
                responseBody.addProperty("Result", isCreated ? "Ok" : "Queue Exist");
                break;
            case "SEND":
                String msg = body.getAsJsonObject().get("Message").getAsString();
                boolean isSend = sendMessage(queueName, msg);
                responseBody.addProperty("Result", isSend ? "Ok" : "Queue Full");
                break;
            case "ACK":
                String ackId = requestUri[3];
                boolean isAck = ackMessage(queueName, ackId);
                responseBody.addProperty("Result", isAck ? "Ok" : "Ack Fail");
                break;
            case "FAIL":
                String failId = requestUri[3];
                boolean isFail = failMessage(queueName, failId);
                responseBody.addProperty("Result", isFail ? "Ok" : "Fail Fail");
                break;
            default:
                break;
        }

        // Generate response 
        resp.setStatus(200);
        resp.getWriter().write(responseBody.toString());
    }
    
    // CREATE
    private boolean createQueue(String queueName, int queueSize, int processTimeout, int maxFailCount, int waitTime) {
        if(RunManager.messageQueueMap.containsKey(queueName)) {
            return false;
        } else {
            RunManager.messageQueueMap.put(queueName, new MessageQueue(queueName, queueSize, processTimeout, maxFailCount, waitTime));
            return true;
        }
    }

    // SEND
    private boolean sendMessage(String queueName, String msg) {
        MessageQueue mq = RunManager.messageQueueMap.get(queueName);        

        mq.update();

        if(mq.isFull()) {
            return false;
        } else {
            return mq.offer(msg);
        }
    }

    // RECEIVE
    private Message receiveMessage(String queueName) {
        MessageQueue mq = RunManager.messageQueueMap.get(queueName);      
        
        boolean isWait = true;
        Message resultMessage = null;

        Date startTime = new Date();
        String currentThreadName = Thread.currentThread().getName();
        while(isWait) {
            mq.update();

            Message msg = mq.poll();
            if(mq.getWaitTime() == 0) {
                // no need to wait - existence is not important 
                resultMessage = msg;    
                isWait = false;
            } else if(msg != null && (RunManager.consumerQueue.isEmpty() || RunManager.consumerQueue.peek().equals(currentThreadName))) {
                // message exists and this consumer's turn
                resultMessage = msg;    
                isWait = false;           

                // get out from consumer queue
                RunManager.consumerQueue.poll();

                System.out.println(String.format("Thread [%s] receive the message [%s]", currentThreadName, msg.getMsg()));
            } else {
                // Check whether wait more
                long waitingTime = new Date().getTime() - startTime.getTime();
                isWait = waitingTime < mq.getWaitTime()*1000L;
                
                if(!RunManager.consumerQueue.contains(currentThreadName)) {
                    RunManager.consumerQueue.offer(currentThreadName);
                    System.out.println(String.format("Thread [%s] starts waiting for its turn.", currentThreadName));
                } 
            }
        } 

        return resultMessage;
    }

    // ACK
    private boolean ackMessage(String queueName, String messageId) {
        MessageQueue mq = RunManager.messageQueueMap.get(queueName);      

        return mq.ack(messageId);
    }

    // FAIL
    private boolean failMessage(String queueName, String messageId) {
        MessageQueue mq = RunManager.messageQueueMap.get(queueName);      

        return mq.fail(messageId);
    }

    // DLQ
    private Message dlqMessage(String queueName) {
        MessageQueue mq = RunManager.messageQueueMap.get(queueName);      
    
        mq.update();

        return mq.pollDlq();
    }
}
