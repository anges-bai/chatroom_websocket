package com.bittech.server;

import com.bittech.util.CommUtil;
import com.bittech.vo.MessageVO;


import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author: BaiMiao
 * @Date: 2020/1/7 15:47
 * @Description:
 */
public class MultiThreadServer {
    private static final Integer PORT;
    static {
        Properties pros=CommUtil.loadProperties("socket.properties");
        PORT=Integer.valueOf(pros.getProperty("PORT"));
    }
    //服务端缓存所有连接的客户端对象
    private static Map<String,Socket> clients
            =new ConcurrentHashMap<>();
    //缓存所有群名称以及群中的成员姓名
    private  static Map<String,Set<String>> groupInfo=new ConcurrentHashMap<>();


    //线程安全问题，用户名重复，谁先来谁先
    //服务端具体处理客户端请求的任务
    private static class ExecuteClient implements Runnable{
        private Socket client;

        private Scanner in;
        private PrintStream out;

        public ExecuteClient(Socket client) {
            this.client = client;
            try {
                this.in=new Scanner(client.getInputStream());
                this.out=new PrintStream(client.getOutputStream(),
                        true,"UTF-8");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (true){
                if (in.hasNextLine()){
                    String strFromClient=in.nextLine();
                    MessageVO msgFromClient=(MessageVO)CommUtil.json2Object(strFromClient,
                            MessageVO.class);
                    if (msgFromClient.getType().equals(1)){
                        //新用户注册
                        String userName=msgFromClient.getContent();
                        //将当前聊天室在线好友信息发回给新用户
                        Set<String> names=clients.keySet();
                        MessageVO msg2Client=new MessageVO();
                        msg2Client.setType(1);
                        msg2Client.setContent(CommUtil.object2Json(names));
                        out.println(CommUtil.object2Json(msg2Client));
                        //将新用户的上线信息发给其他在线用户
                        String loginMsg="newLogin:"+userName;
                        for (Socket socket:clients.values()){
                            try {
                                PrintStream out=new PrintStream(socket.getOutputStream(),
                                        true,"UTF-8");
                                out.println(loginMsg);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        //将新用户信息保存到当前服务端
                        System.out.println(userName+"上线了！");
                        clients.put(userName,client);
                        System.out.println("当前聊天室在线人数为:"+clients.size());
                    }
                    else if (msgFromClient.getType().equals(2)){
                        //私聊信息
                        String friendName=msgFromClient.getTo();
                        Socket socket=clients.get(friendName);
                        try {
                            PrintStream out=new PrintStream(socket.getOutputStream(),
                                    true,"UTF-8");
                            MessageVO msg2Client=new MessageVO();
                            msg2Client.setType(2);
                            msg2Client.setContent(msgFromClient.getContent());
                            out.println(CommUtil.object2Json(msg2Client));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }else if (msgFromClient.getType().equals(3)){
                        //注册群消息
                        String groupName=msgFromClient.getContent();
                        Set<String> friends=(Set<String>)CommUtil.json2Object(msgFromClient.getTo(),
                                Set.class);
                        groupInfo.put(groupName,friends);
                        System.out.println("注册群成功，当前共有"+groupInfo.size()+"个群");
                    }else if (msgFromClient.getType().equals(4)){
                        //content:senderName-msg
                        //to:groupName
                        String groupName=msgFromClient.getTo();
                        Set<String> friends=groupInfo.get(groupName);
                        //将群聊信息转发到相应客户端
                        Iterator<String> iterator=friends.iterator();
                        while (iterator.hasNext()){
                            String clientName=iterator.next();
                            Socket client=clients.get(clientName);
                            try {
                                PrintStream out=new PrintStream(client.getOutputStream(),
                                        true,"UTF-8");
                                MessageVO messageVO=new MessageVO();
                                messageVO.setType(4);
                                messageVO.setContent(msgFromClient.getContent());
                                messageVO.setTo(groupInfo+"-"+CommUtil.object2Json(friends));
                                out.println(CommUtil.object2Json(messageVO));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        //1.建立基站
        ServerSocket server=new ServerSocket(PORT);
        //2.新建线程
        ExecutorService executor=Executors.newFixedThreadPool(50);
        for (int i = 0; i <50 ; i++) {
            System.out.println("等待客户端连接");
            Socket client=server.accept();
            System.out.println("有新的客户端连接..端口号为:"+client.getPort());
            executor.submit(new ExecuteClient(client));

        }
    }
}
