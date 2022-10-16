package edu.utfpr.dtnmessenger;

import android.net.wifi.p2p.WifiP2pDevice;
import android.util.Log;

import java.net.NetworkInterface;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NodeInformationObject {

    private final int STORE_AND_FORWARD_TTL_POLICY = 10 * 60 * 1000; //2 minutes = 2 lots of 60 (seconds in a minute) lots of 1000 (milliseconds in a second)
    //Information about the node itself
    private String thisDeviceAddress;
    private String myPublicKeyString;
    private KeyPair myKeyPair;
    private boolean isInitialized = false; //when a key pair is set this is set to true
    private boolean hasServerThread = false;
    private boolean isServer = false;            //Whether the node itself is server or not
    private boolean isConnected = false;        //Success flag of the last WiFi connection
    private int groupOwnerIndex = -2;            //Index(>=0) of group owner in the WifiP2pDevice array list, -1 represents the node itself is group owner, -2 represents no group has been formed
    private final HashMap<String, String> myContacts = new HashMap<String, String>();
    private final ConcurrentHashMap<Integer, MessageObject> storeAndForwardMessages = new ConcurrentHashMap<Integer, MessageObject>();
    private final List<MessageObject> allReceivedMessages = new ArrayList<MessageObject>();
    private final List<MessageObject> allTransmittedMessages = new ArrayList<MessageObject>();

    //Information about peers
    private final List<WifiP2pDevice> wifiP2PDevices = new ArrayList<WifiP2pDevice>();

    //Public constructor
    public NodeInformationObject() {
        thisDeviceAddress = getWFDMacAddress();
        Log.i("P2PMessenger", "(NodeInformationObject) This device address is: " + thisDeviceAddress);
    }

    //https://stackoverflow.com/questions/63784361/wifi-direct-this-device-address-not-work-on-android-10
    public String getWFDMacAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ntwInterface : interfaces) {

                if (ntwInterface.getName().equalsIgnoreCase("p2p0")) {
                    byte[] byteMac = ntwInterface.getHardwareAddress();
                    if (byteMac == null) {
                        return null;
                    }
                    StringBuilder strBuilder = new StringBuilder();
                    for (byte b : byteMac) {
                        strBuilder.append(String.format("%02X:", b));
                    }

                    if (strBuilder.length() > 0) {
                        strBuilder.deleteCharAt(strBuilder.length() - 1);
                    }

                    return strBuilder.toString().toLowerCase();
                }

            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void setDeviceAddress(String deviceAddress) {
        thisDeviceAddress = deviceAddress;
    }

    public String getThisDeviceAddress() {
        return thisDeviceAddress;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void resetConnection() {
        hasServerThread = false;
        isServer = false;
        isConnected = false;
        groupOwnerIndex = -2;
        wifiP2PDevices.clear();
    }

    public void addMessageToStoreAndForward(MessageObject messageObject) {
        storeAndForwardMessages.put(messageObject.hashCode(), new MessageObject(messageObject));
    }

    public void addTransmittedMessage(MessageObject messageObject) {
        allTransmittedMessages.add(new MessageObject(messageObject));
    }

    public String getMyPublicKeyString() {
        return myPublicKeyString;
    }


    public KeyPair getMyKeyPair() {
        return myKeyPair;
    }

    public void setMyKeyPair(KeyPair myKeyPair) {
        this.myKeyPair = myKeyPair;
        myPublicKeyString = Cryptography.getPublicKeyString(myKeyPair.getPublic());
        isInitialized = true;
    }

    public HashMap<String, String> getMyContacts() {
        return myContacts;
    }

    public void addContact(String contactName, String publicKey) {
        this.myContacts.put(contactName, publicKey);
    }

    public ConcurrentHashMap<Integer, MessageObject> getStoreAndForwardMessages() {
        return storeAndForwardMessages;
    }

    public boolean hasPreviouslyReceivedThisMessage(MessageObject messageToBeChecked) {
        for (MessageObject message : allReceivedMessages) {
            if (message.hashCode() == messageToBeChecked.hashCode()) {
                return true;
            }
        }
        return false;
    }

    public void addReceivedMessage(MessageObject messageObject) {
        this.allReceivedMessages.add(messageObject);
    }

    public int getStoreAndForwardTTLPolicy() {
        return STORE_AND_FORWARD_TTL_POLICY;
    }

    public boolean hasServerThread() {
        return hasServerThread;
    }

    public void setHasServerThread(boolean hasServerThread) {
        this.hasServerThread = hasServerThread;
    }

    public void setIsServer(boolean isServer) {
        this.isServer = isServer;
    }

    public boolean isServer() {
        return isServer;
    }

    public void setIsConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public int getGroupOwnerIndex() {
        return groupOwnerIndex;
    }

    public void setGroupOwnerIndex(int groupOwnerIndex) {
        this.groupOwnerIndex = groupOwnerIndex;
    }

    public void addWifiP2pDevice(WifiP2pDevice wifiP2PDevice) {
        this.wifiP2PDevices.add(wifiP2PDevice);
    }

    public WifiP2pDevice getWifiP2pDevice(int index) {
        return wifiP2PDevices.get(index);
    }
}
