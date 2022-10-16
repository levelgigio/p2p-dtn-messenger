package edu.utfpr.dtnmessenger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class SocketThread extends Thread {

    private Socket socket = null;

    //The stream which gets data from the socket and pipes to server thread
    private final DataOutputStream out;
    //The stream which gets data from server thread and writes to socket
    private final DataInputStream in;

    public SocketThread(Socket socket, OutputStream out, InputStream in) {
        this.socket = socket;
        this.out = new DataOutputStream(out);
        this.in = new DataInputStream(in);
    }

    @Override
    public void run() {
        int len;
        byte[] buffer = new byte[1024];

        try {
            //If this code is reached, a peer has connected and transferred data
            InputStream inputstream = socket.getInputStream();
            OutputStream outputstream = socket.getOutputStream();

            while (!this.isInterrupted()) {
                // Read data from the socket and pipe to UI thread or other peer thread
                if (inputstream.available() > 0) {
                    len = inputstream.read(buffer);
                    if (len > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                //Read data form UI thread or other peer threads and write to the socket
                if (in.available() > 0) {
                    len = in.read(buffer);
                    if (len > 0) {
                        outputstream.write(buffer, 0, len);
                    }
                }
            }
        } catch (IOException e) {
            //Catch logic
        } finally {
            //Close the sockets when any exceptions occur
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