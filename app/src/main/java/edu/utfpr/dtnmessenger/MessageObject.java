package edu.utfpr.dtnmessenger;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;

public class MessageObject {
    private final int DEFAULT_DATE_LENGTH = 13;
    private final int DEFAULT_PUB_KEY_LENGTH = Cryptography.getDefaultPublicKeyLength();
    private final int DEFAULT_AES_KEY_LENGTH = Cryptography.getDefaultAESKeyLength();
    Calendar calendar = Calendar.getInstance();
    private Integer messageType;    //Message type
    private byte[] creationDateBuffer = new byte[DEFAULT_DATE_LENGTH];
    private byte[] sourceBuffer = new byte[DEFAULT_PUB_KEY_LENGTH];
    private byte[] destinationBuffer = new byte[DEFAULT_PUB_KEY_LENGTH];
    private byte[] AESKeyBuffer = new byte[DEFAULT_AES_KEY_LENGTH];
    private int messageBodyLength = 0;    //Message length
    private byte[] messageBodyBuffer;

    public MessageObject() {
        setCreationDateUNIXTimestamp();
    }

    //Clone constructor
    public MessageObject(MessageObject messageObject) {
        this.messageType = messageObject.getMessageType();
        this.creationDateBuffer = messageObject.getCreationDateBuffer();
        this.sourceBuffer = messageObject.getSourceBuffer();
        this.destinationBuffer = messageObject.getDestinationBuffer();
        this.AESKeyBuffer = messageObject.getAESKeyBuffer();
        this.messageBodyLength = messageObject.getMessageBodyLength();
        this.messageBodyBuffer = messageObject.getMessageBodyBuffer();
    }

    public byte[] getCreationDateBuffer() {
        return creationDateBuffer;
    }

    public byte[] getSourceBuffer() {
        return sourceBuffer;
    }

    public byte[] getDestinationBuffer() {
        return destinationBuffer;
    }

    public byte[] getAESKeyBuffer() {
        return AESKeyBuffer;
    }

    public int getMessageBodyLength() {
        return messageBodyLength;
    }

    public byte[] getMessageBodyBuffer() {
        return messageBodyBuffer;
    }

    @Override
    public int hashCode() {
        String concatMessage =
                messageType.toString() +
                        new String(creationDateBuffer) +
                        new String(sourceBuffer) +
                        new String(destinationBuffer) +
                        new String(AESKeyBuffer) +
                        messageBodyLength +
                        new String(messageBodyBuffer);

        return concatMessage.hashCode();
    }

    public void sendMessage(OutputStream outputStream) {
        try {
            //Type 2 messages does not send AESKey and its body is not cyphered
            if (messageType == 2) {
                outputStream.write(messageType);
                outputStream.write(creationDateBuffer, 0, DEFAULT_DATE_LENGTH);
                outputStream.write(sourceBuffer, 0, DEFAULT_PUB_KEY_LENGTH);
                outputStream.write(destinationBuffer, 0, DEFAULT_PUB_KEY_LENGTH);
                outputStream.write(messageBodyLength);
                if (messageBodyLength > 0) {
                    outputStream.write(messageBodyBuffer, 0, messageBodyLength);
                }
            } else {
                outputStream.write(messageType);
                outputStream.write(creationDateBuffer, 0, DEFAULT_DATE_LENGTH);
                outputStream.write(sourceBuffer, 0, DEFAULT_PUB_KEY_LENGTH);
                outputStream.write(destinationBuffer, 0, DEFAULT_PUB_KEY_LENGTH);
                outputStream.write(messageBodyLength);
                if (messageBodyLength > 0) {
                    outputStream.write(AESKeyBuffer, 0, DEFAULT_AES_KEY_LENGTH);
                    outputStream.write(messageBodyBuffer, 0, messageBodyLength);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readBytesToBuffer(InputStream inputStream, int length, byte[] buffer) {
        int len = 0;
        while (len < length) {
            try {
                len += inputStream.read(buffer, len, length - len);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public MessageObject readMessage(InputStream inputStream) {
        try {
            messageType = inputStream.read();
            readBytesToBuffer(inputStream, DEFAULT_DATE_LENGTH, creationDateBuffer);
            readBytesToBuffer(inputStream, DEFAULT_PUB_KEY_LENGTH, sourceBuffer);
            readBytesToBuffer(inputStream, DEFAULT_PUB_KEY_LENGTH, destinationBuffer);
            messageBodyLength = inputStream.read();
            if (messageBodyLength > 0) {
                messageBodyBuffer = new byte[messageBodyLength];
                if (messageType == 2) {
                    readBytesToBuffer(inputStream, messageBodyLength, messageBodyBuffer);
                } else {
                    readBytesToBuffer(inputStream, DEFAULT_AES_KEY_LENGTH, AESKeyBuffer);
                    readBytesToBuffer(inputStream, messageBodyLength, messageBodyBuffer);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    public Integer getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public long getCreationDateUNIXTimestamp() {
        return new Long(new String(creationDateBuffer));
    }

    public void setCreationDateUNIXTimestamp() {
        long date = calendar.getTime().getTime();
        this.creationDateBuffer = String.valueOf(date).getBytes(StandardCharsets.UTF_8);
    }

    public String getMessageSource() {
        return new String(sourceBuffer);
    }

    public void setMessageSource(String messageSource) {
        this.sourceBuffer = messageSource.getBytes(StandardCharsets.UTF_8);
    }

    public String getMessageDestination() {
        return new String(destinationBuffer);
    }

    public void setMessageDestination(String messageDestination) {
        this.destinationBuffer = messageDestination.getBytes(StandardCharsets.UTF_8);
    }


    private String getAESKey() {
        return new String(AESKeyBuffer);
    }

    //For use with messages with type = 2 (uncrypted)
    public String getMessageBody() {
        try {
            if (messageBodyLength > 0) {
                return new String(messageBodyBuffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setMessageBody(String messageBody) {
        //Only works if we set message type first
        try {
            //A type 2 message represents an plain message (not encrypted)
            if (messageType == 2) {
                byte[] messageBytes = messageBody.getBytes(StandardCharsets.UTF_8);
                messageBodyLength = messageBytes.length;
                messageBodyBuffer = new byte[messageBodyLength];
                this.messageBodyBuffer = messageBytes;
                //Other message types should be encrypted
                // Only works if we set message destination first
            } else {
                AESKeyBuffer = Cryptography.getNewAESSecretKey().getBytes(StandardCharsets.UTF_8);
                PublicKey destinationPubKey =
                        Cryptography.getPublicKeyFromString(new String(destinationBuffer));
                String encryptedMessage = Cryptography.encryptAES(messageBody, getAESKey());
                String cypherSecretKey = Cryptography.encryptRSA(destinationPubKey, getAESKey());
                byte[] encryptedMessageBytes = encryptedMessage.getBytes(StandardCharsets.UTF_8);
                Log.i("P2PMessenger", "(MessageObject) Setting ecrypted message body: " + encryptedMessage);

                messageBodyLength = encryptedMessageBytes.length;
                Log.i("P2PMessenger", "(MessageObject) Ecrypted message body length: " + messageBodyLength);

                messageBodyBuffer = new byte[messageBodyLength];
                this.messageBodyBuffer = encryptedMessageBytes;
                this.AESKeyBuffer = cypherSecretKey.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            //Catch logic
        }
    }

    //For use with encrypted messages
    public String getMessageBody(PrivateKey myRSAPrivateKey) {
        try {
            if (messageBodyLength > 0) {
                Log.i("P2PMessenger", "(MessageObject) Cyphered message: " + new String(messageBodyBuffer));

                String secretKeyDeciphered =
                        Cryptography.decryptRSA(myRSAPrivateKey, new String(AESKeyBuffer));
                Log.i("P2PMessenger", "(MessageObject) AES Key Deciphered: " + secretKeyDeciphered);
                String decipheredMessage =
                        Cryptography.decryptAES(new String(messageBodyBuffer), secretKeyDeciphered);
                Log.i("P2PMessenger", "(MessageObject) Message Deciphered: " + decipheredMessage);
                return decipheredMessage;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
