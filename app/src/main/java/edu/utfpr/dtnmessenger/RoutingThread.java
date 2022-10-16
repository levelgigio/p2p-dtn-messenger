package edu.utfpr.dtnmessenger;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingThread extends Thread {
    String myPublicKey;
    MessageObject messageObject;

    //The stream which writes data to UI thread or client peers
    private DataOutputStream outUI = null;
    Map<String, DataOutputStream> mapOutTransmitClient = new ConcurrentHashMap<String, DataOutputStream>();

    //The stream which gets data from UI thread or client peers
    private DataInputStream inUI = null;
    Map<String, DataInputStream> mapInReceiveClient = new ConcurrentHashMap<String, DataInputStream>();

    public RoutingThread(String myPublicKey, PipedOutputStream outUI, PipedInputStream inUI) {
        this.myPublicKey = myPublicKey;
        this.outUI = new DataOutputStream(outUI);
        this.inUI = new DataInputStream(inUI);
    }

    public void addClient(String clientPubKey, PipedOutputStream outTransmitClient, PipedInputStream inReceiveClient) {
        Log.i("P2PMessenger", "(RoutingThread) New route to client: " + clientPubKey);
        mapOutTransmitClient.put(clientPubKey, new DataOutputStream(outTransmitClient));
        mapInReceiveClient.put(clientPubKey, new DataInputStream(inReceiveClient));
    }

    public void removeClient(String clientPubKey) {
        mapOutTransmitClient.remove(clientPubKey);
        mapInReceiveClient.remove(clientPubKey);
    }

    //Forward messages according to message destination
    public void MsgForward(DataInputStream in) {
        String destinationPubKey;

        try {
            if (in.available() > 0) {
                messageObject = new MessageObject();
                messageObject.readMessage(in);
                destinationPubKey = messageObject.getMessageDestination();
                //Get the destination and forward it to the corresponding pipe
                if (destinationPubKey.equals(myPublicKey)) {
                    Log.i("P2PMessenger", "(RoutingThread) Routing to server");
                    messageObject.sendMessage(outUI);
                } else if (mapOutTransmitClient.containsKey(destinationPubKey)) {
                    Log.i("P2PMessenger", "(RoutingThread) Routing to client: " + destinationPubKey);
                    messageObject.sendMessage(mapOutTransmitClient.get(destinationPubKey));
                } else {
                    //If destination is not in group, send the message to everybody
                    //When a node receive a message that is not for it, that node adds to its
                    //store-and-forward message buffer
                    messageObject.sendMessage(outUI);
                    for (String key : mapOutTransmitClient.keySet()) {
                        messageObject.sendMessage(mapOutTransmitClient.get(key));
                    }
                }
            }
        } catch (IOException e) {
            //Catch logic
        }
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            //Read data from the input stream and route to corresponding output streams
            MsgForward(inUI);
            for (String key : mapInReceiveClient.keySet()) {
                MsgForward(mapInReceiveClient.get(key));
            }
        }
    }
}
