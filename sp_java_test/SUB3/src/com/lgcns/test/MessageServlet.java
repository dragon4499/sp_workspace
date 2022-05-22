package com.lgcns.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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
        switch(requestUri[1]) {
            case "RECEIVE":                
                Message msg = receiveMessage(queueName);

                if(msg != null) {
                    responseBody.addProperty("Result", "Ok");
                    responseBody.addProperty("MessageID", msg.getId());
                    responseBody.addProperty("Message", msg.getMsg());
                } else {
                    responseBody.addProperty("Result", "No Message");
                }
                break;
            default:
                break;
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
                boolean isCreated = createQueue(queueName, queueSize);
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
    private boolean createQueue(String queueName, int queueSize) {
        if(RunManager.messageQueueMap.containsKey(queueName)) {
            return false;
        } else {
            RunManager.messageQueueMap.put(queueName, new MessageQueue(queueSize));
            return true;
        }
    }

    // SEND
    private boolean sendMessage(String queueName, String msg) {
        MessageQueue mq = RunManager.messageQueueMap.get(queueName);        

        if(mq.isFull()) {
            return false;
        } else {
            return mq.offer(msg);
        }
    }

    // RECEIVE
    private Message receiveMessage(String queueName) {
        MessageQueue mq = RunManager.messageQueueMap.get(queueName);      
          
        return mq.poll();
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
}
