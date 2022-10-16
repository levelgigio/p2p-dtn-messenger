package edu.utfpr.dtnmessenger;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClientThread extends Thread {
    private final String hostAddress;
    private final NodeInformationObject nodeInformationObject;

    // The stream which gets data from the socket and piped to UI thread
    private final DataOutputStream outUI;
    // The stream which gets data from UI thread and writes to socket
    private final DataInputStream inUI;

    public ClientThread(
            String hostAddress,
            OutputStream outUI,
            InputStream inUI,
            NodeInformationObject nodeInformationObject) {
        this.hostAddress = hostAddress;
        this.outUI = new DataOutputStream(outUI);
        this.inUI = new DataInputStream(inUI);
        this.nodeInformationObject = nodeInformationObject;
    }

    // Returns ack message from server from which we can extract server's pubKey
    // and server store-and-forward buffer size
    private MessageObject getServerAcknowledgmentMessage(InputStream inputStream) {
        try {
            MessageObject messageObject = new MessageObject();
            while (messageObject.getMessageType() == null) {
//                Log.i("P2PMessenger", "(ClientThread) Waiting for initial server message");
                if (inputStream.available() > 0) {
                    Log.i("P2PMessenger", "(ClientThread) Reading initial server message");
                    messageObject.readMessage(inputStream);
                }
            }
            Log.i(
                    "P2PMessenger",
                    "(ClientThread) Ack Message received from server:"
//                            + " Source: " + messageObject.getMessageSource()
//                            + " Type: " + messageObject.getMessageType()
                            + " Body: " + messageObject.getMessageBody()
            );
            return messageObject;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //Loop to receive a amount of store and forward messages from server
    private void getServerStoreAndForwardMessages(InputStream inputStream, int bufferLength) {
        try {
            for (int i = 0; i < bufferLength; i++) {
                MessageObject messageObject = new MessageObject();
                while (messageObject.getMessageType() == null) {
                    Log.i("P2PMessenger", "(ClientThread) Waiting message #" + i + " from server buffer");
                    if (inputStream.available() > 0) {
                        Log.i("P2PMessenger", "(ClientThread) Reading message #" + i + " from server buffer");
                        messageObject.readMessage(inputStream);
                    }
                }
                messageObject.sendMessage(outUI);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Sends to the server a message that contains some information in the body
    private void sendClientAckMessage(OutputStream outputStream, String messageBody) {
        MessageObject messageObject = new MessageObject();
        messageObject.setMessageType(2);
        messageObject.setMessageSource(nodeInformationObject.getMyPublicKeyString());
        messageObject.setMessageDestination(nodeInformationObject.getMyPublicKeyString()); // Doesn`t make difference in this message
        messageObject.setMessageBody(messageBody);
        messageObject.sendMessage(outputStream);
        Log.i(
                "P2PMessenger",
                "(ClientThread) Message to be sent:"
//                        + " Source: " + messageObject.getMessageSource()
//                        + " Type: " + messageObject.getMessageType()
                        + " Body: " + messageObject.getMessageBody()
        );
        Log.i("P2PMessenger", "(ClientThread) Message sent");
    }

    private void sendClientStoreAndForwardMessages(OutputStream outputStream) {
        for (Integer messageHashCode : nodeInformationObject.getStoreAndForwardMessages().keySet()) {
            MessageObject message = nodeInformationObject.getStoreAndForwardMessages().get(messageHashCode);
            message.sendMessage(outputStream);
        }
    }

    @Override
    public void run() {
        // Start to transmit data to another peer
        int port = 2468;
        int messageLength;
        Socket socket = null;
        OutputStream outputStream = null;
        InputStream inputStream = null;
        byte[] messageBuffer = new byte[1024];
        boolean isConnectionSuccess = false;

        while (!isConnectionSuccess) {
            try {
                // Create a client socket with the host, port, and timeout information.
                socket = new Socket();
                socket.bind(null);
                socket.connect((new InetSocketAddress(hostAddress, port)), 5000);
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();

                Log.i("P2PMessenger", "(ClientThread) Sending client's device address");
                sendClientAckMessage(outputStream, nodeInformationObject.getThisDeviceAddress());
                Log.i("P2PMessenger", "(ClientThread) Sending client's pubkey and buffer size");
                sendClientAckMessage(outputStream, String.valueOf(nodeInformationObject.getStoreAndForwardMessages().size()));
                Log.i("P2PMessenger", "(ClientThread) Sending client's store-and-forward messages");
                sendClientStoreAndForwardMessages(outputStream);

                Log.i(
                        "P2PMessenger",
                        "(ClientThread) Trying to get server's pubkey and Store-and-Forward buffer size");
                MessageObject serverAcknowledgmentMessage = getServerAcknowledgmentMessage(inputStream);

                int bufferSize = Integer.parseInt(serverAcknowledgmentMessage.getMessageBody());
                getServerStoreAndForwardMessages(inputStream, bufferSize);

                while (!this.isInterrupted()) {
                    // Read data from the socket and pipe to UI thread
                    if (inputStream.available() > 0) {
                        messageLength = inputStream.read(messageBuffer);
                        if (messageLength > 0) {
                            Log.i("P2PMessenger", "(ClientThread) Receiving a message");
                            outUI.write(messageBuffer, 0, messageLength);
                        }
                    }

                    // Read data from UI thread and write to the socket
                    if (inUI.available() > 0) {
                        messageLength = inUI.read(messageBuffer);
                        if (messageLength > 0) {
                            Log.i("P2PMessenger", "(ClientThread) Sending a message");
                            outputStream.write(messageBuffer, 0, messageLength);
                        }
                    }
                }
            } catch (IOException e) {
                // Catch logic
            }
            // Clean up any open sockets when done transferring or if an exception occurred
            finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        // Catch logic
                    }
                }
                if (socket != null) {
                    if (socket.isConnected()) {
                        isConnectionSuccess = true;
                    }
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // Catch logic
                    }
                }
            }
        }
    }
}
