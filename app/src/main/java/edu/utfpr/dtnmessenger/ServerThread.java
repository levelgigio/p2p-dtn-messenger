package edu.utfpr.dtnmessenger;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerThread extends Thread {
    Map<String, PipedOutputStream> mapOutTransmitClient = new ConcurrentHashMap<String, PipedOutputStream>();
    Map<String, PipedOutputStream> mapOutReceiveClient = new ConcurrentHashMap<String, PipedOutputStream>();
    Map<String, PipedInputStream> mapInReceiveClient = new ConcurrentHashMap<String, PipedInputStream>();
    Map<String, PipedInputStream> mapInTransmitClient = new ConcurrentHashMap<String, PipedInputStream>();
    //Map mac address to pub key
    Map<String, String> mapDeviceAddress2PublicKey = new ConcurrentHashMap<String, String>();
    private final NodeInformationObject nodeInformationObject;
    //Thread handlers
    private RoutingThread routingThread = null;
    private final Map<String, SocketThread> mapSocketThread = new ConcurrentHashMap<String, SocketThread>();
    //The stream which writes data to UI thread or client peers
    private PipedOutputStream outUI = null;
    //The stream which gets data from UI thread or client peers
    private PipedInputStream inUI = null;

    public ServerThread(
            PipedOutputStream outUI,
            PipedInputStream inUI,
            NodeInformationObject nodeInformationObject) {
        this.outUI = outUI;
        this.inUI = inUI;
        this.nodeInformationObject = nodeInformationObject;
    }

    //Create pipes for new connected client and add it to routing thread
    private void addNewClient(String clientDeviceAddress, String clientPubKey, Socket socket) {
        try {
            mapDeviceAddress2PublicKey.put(clientDeviceAddress, clientPubKey);

            mapOutTransmitClient.put(clientPubKey, new PipedOutputStream());
            mapInTransmitClient.put(clientPubKey, new PipedInputStream(mapOutTransmitClient.get(clientPubKey)));

            mapOutReceiveClient.put(clientPubKey, new PipedOutputStream());
            mapInReceiveClient.put(clientPubKey, new PipedInputStream(mapOutReceiveClient.get(clientPubKey)));

            routingThread.addClient(clientPubKey, mapOutTransmitClient.get(clientPubKey), mapInReceiveClient.get(clientPubKey));

            mapSocketThread.put(clientPubKey, new SocketThread(socket, mapOutReceiveClient.get(clientPubKey), mapInTransmitClient.get(clientPubKey)));
            mapSocketThread.get(clientPubKey).setDaemon(true);
            mapSocketThread.get(clientPubKey).start();
        } catch (Exception e) {
            //catch
        }
    }

    public void removeClient(String clientDeviceAddress) {
        String clientPubKey = mapDeviceAddress2PublicKey.get(clientDeviceAddress);

        mapDeviceAddress2PublicKey.remove(clientDeviceAddress);

        mapOutTransmitClient.remove(clientPubKey);
        mapInTransmitClient.remove(clientPubKey);

        mapOutReceiveClient.remove(clientPubKey);
        mapInReceiveClient.remove(clientPubKey);

        routingThread.removeClient(clientPubKey);

        mapSocketThread.remove(clientPubKey);
    }

    //Returns an ack message from the client
    //Wait until message object is populated.
    //We check against Message Type because every message should have a type
    //but not all messages have a body, for example.
    private MessageObject getClientAckMessage(InputStream inputStream) {
        try {
            MessageObject messageObject = new MessageObject();
            while (messageObject.getMessageType() == null) {
//				Log.i("P2PMessenger", "(ServerThread) Waiting for initial message");
                if (inputStream.available() > 0) {
                    Log.i("P2PMessenger", "(ServerThread) Reading initial message");
                    messageObject.readMessage(inputStream);
                }
            }
            Log.i("P2PMessenger", "(ServerThread) Message received:"
//                + " Source: " + messageObject.getMessageSource()
//                + " Type: " + messageObject.getMessageType()
                            + " Body: " + messageObject.getMessageBody()
            );
            return messageObject;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void getClientStoreAndForwardMessages(InputStream inputStream, int bufferLength) {
        try {
            for (int i = 0; i < bufferLength; i++) {
                MessageObject messageObject = new MessageObject();
                while (messageObject.getMessageType() == null) {
                    Log.i("P2PMessenger", "(ServerThread) Waiting message #" + i + " from server buffer");
                    if (inputStream.available() > 0) {
                        Log.i(
                                "P2PMessenger", "(ServerThread) Reading message #" + i + " from server buffer");
                        messageObject.readMessage(inputStream);
                    }
                }
                messageObject.sendMessage(outUI);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Sends to the server a message that contains this client's Pub Key,
    // so that the server knows how to route messages to it
    private void sendServerAcknowledgmentMessage(OutputStream outputStream) {
        MessageObject messageObject = new MessageObject();
        messageObject.setMessageType(2);
        messageObject.setMessageSource(nodeInformationObject.getMyPublicKeyString());
        messageObject.setMessageDestination(nodeInformationObject.getMyPublicKeyString()); //Doesn`t make difference in this message
        messageObject.setMessageBody(String.valueOf(nodeInformationObject.getStoreAndForwardMessages().size()));
        Log.i("P2PMessenger", "(ServerThread) Message to be sent:"
//                + " Source: " + messageObject.getMessageSource()
//                + " Type: " + messageObject.getMessageType()
                        + " Body: " + messageObject.getMessageBody()
        );
        messageObject.sendMessage(outputStream);
        Log.i("P2PMessenger", "(ServerThread) Message sent");
    }

    private void sendServerStoreAndForwardMessages(OutputStream outputStream) {
        for (Integer messageHashCode : nodeInformationObject.getStoreAndForwardMessages().keySet()) {
            MessageObject message = nodeInformationObject.getStoreAndForwardMessages().get(messageHashCode);
            message.sendMessage(outputStream);
        }
    }

    @Override
    public void run() {
        int port = 2468;
        ServerSocket serverSocket = null;
        Socket socket = null;

        try {
            //Create a receiver socket and wait for peer connections
            // This call blocks until a connection is accepted from a peer
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(100);

            //Start the routing thread
            routingThread = new RoutingThread(nodeInformationObject.getMyPublicKeyString(), outUI, inUI);
            routingThread.setDaemon(true);
            routingThread.start();

            //Listen to connection request and start a new thread to handle it when a request is accepted
            while ((!this.isInterrupted())) {
                try {
                    socket = serverSocket.accept();
                    if (socket != null) {
                        OutputStream outputStream = socket.getOutputStream();
                        InputStream inputStream = socket.getInputStream();

                        //Getting ack messages from this new client
                        MessageObject clientDeviceAddressAckMessage = getClientAckMessage(inputStream);
                        Log.i("P2PMessenger", "(ServerThread) New client with address: " + clientDeviceAddressAckMessage.getMessageBody());
                        MessageObject clientBufferAckMessage = getClientAckMessage(inputStream);
                        int bufferSize = Integer.parseInt(clientBufferAckMessage.getMessageBody());
                        getClientStoreAndForwardMessages(inputStream, bufferSize);

                        sendServerAcknowledgmentMessage(outputStream);
                        sendServerStoreAndForwardMessages(outputStream);

                        String clientPubKey = clientBufferAckMessage.getMessageSource();

                        String clientDeviceAddress = clientDeviceAddressAckMessage.getMessageBody();

                        //With the clients pubkey we can add establish the pipes and add it to the routing thread
                        addNewClient(clientDeviceAddress, clientPubKey, socket);

                        Log.i("P2PMessenger", "(ServerThread) Notifying UI of a new connection with client: " + clientPubKey);
                        //Notify the server's UI about the network topology update
                        MessageObject messageObject = new MessageObject();
                        messageObject.setMessageType(1);
                        messageObject.setMessageSource(nodeInformationObject.getMyPublicKeyString());
                        messageObject.setMessageDestination(nodeInformationObject.getMyPublicKeyString());
                        messageObject.sendMessage(outUI);

                        Log.i("P2PMessenger", "(ServerThread) Notifying clients of a new connection");
                        //Notify all the clients about the network topology update
                        for (String key : mapOutTransmitClient.keySet()) {
                            messageObject = new MessageObject();
                            messageObject.setMessageType(1);//Message Type
                            messageObject.setMessageSource(nodeInformationObject.getMyPublicKeyString());    //Message Source
                            messageObject.setMessageDestination(key);    //Message Destination
                            messageObject.sendMessage(mapOutTransmitClient.get(key));
                        }

                        Log.i("P2PMessenger", "(ServerThread) Epidemic messaging");
                        //Notify all the clients about the network topology update
                        for (String key : mapOutTransmitClient.keySet()) {
                            if (!key.equals(clientPubKey)) {
                                for (Integer messageHashCode : nodeInformationObject.getStoreAndForwardMessages().keySet()) {
                                    MessageObject message = nodeInformationObject.getStoreAndForwardMessages().get(messageHashCode);
                                    message.sendMessage(mapOutTransmitClient.get(key));
                                }
                            }
                        }
                        Log.i("P2PMessenger", "(ServerThread) All notified and received buffer messages");
                    }
                } catch (IOException e) {
                    //Catch logic
                }
            }
        } catch (IOException e) {
            //Catch logic
        } finally {
            //Interrupt the threads
            if (routingThread != null) {
                routingThread.interrupt();
                routingThread = null;
            }
//            for (String key : socketThreadList.keySet()) {
//                socketThreadList.get(key).interrupt();
//                socketThreadList.remove(key);
//            }

            //Close the sockets when any exceptions occur
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    //Catch logic
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    //Catch logic
                }
            }
        }
    }
}