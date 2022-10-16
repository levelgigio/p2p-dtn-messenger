package edu.utfpr.dtnmessenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
    private final WifiP2pManager manager;
    private final Channel channel;
    private final MainActivity activity;
    private ArrayList<WifiP2pDevice> lastConnectedPeers = new ArrayList<WifiP2pDevice>();
    private final ArrayList<WifiP2pDevice> nearbyPeers = new ArrayList<WifiP2pDevice>();
    /*Mac address of device. This is used to map addresses to public keys, because when a
    disconnect event happens here in the broadcast receiver, we can only use the mac address
    to remove the public keys from the routing thread*/
    private String thisDeviceAddress;


    private final PeerListListener myPeerListListener =
            new PeerListListener() {
                @Override
                public void onPeersAvailable(WifiP2pDeviceList peerList) {
                    // Clear old peers to add the current nearby peers
                    nearbyPeers.clear();
                    // Filter only devices with name starting with [P2P], this is because otherwise
                    // it would try to connect to devices like my smart tv ([TV] Samsung 7 Series (49))
                    for (WifiP2pDevice device : peerList.getDeviceList()) {
                        if (device.deviceName.contains("[P2P]")) {
                            nearbyPeers.add(device);
                        }
                    }
                    if (nearbyPeers.isEmpty()) {
                        return;
                    }

                    // Add the devices to node info object and find the index of group owner
                    for (int i = 0; i < nearbyPeers.size(); i++) {
                        activity.getNodeInformationObject().addWifiP2pDevice(nearbyPeers.get(i));
                        if (nearbyPeers.get(i).isGroupOwner()) {
                            activity.getNodeInformationObject().setGroupOwnerIndex(i);
                        }
                    }

                    // Decide which peer to connect
                    int index;
                    // No group has been formed yet, get a peer and connect
                    if (activity.getNodeInformationObject().getGroupOwnerIndex() == -2) {
                        Log.i("P2PMessenger", "(BroadcastReceiver) No group owner found");
                        index = 0;
                    }
                    // A group exists, find the group owner and connect
                    else {
                        Log.i("P2PMessenger", "(BroadcastReceiver) A group owner has been found");
                        index = activity.getNodeInformationObject().getGroupOwnerIndex();
                        Log.i(
                                "P2PMessenger",
                                "(BroadcastReceiver) Owner name: "
                                        + activity.getNodeInformationObject().getWifiP2pDevice(index).deviceName);
                    }

                    // Connect to the designated peer
                    if (!activity.getNodeInformationObject().isConnected()) {
                        Log.i("P2PMessenger", "(BroadcastReceiver) Connecting");
                        WifiP2pDevice targetNode = activity.getNodeInformationObject().getWifiP2pDevice(index);
                        Log.i("P2PMessenger", "(BroadcastReceiver) Target name:" + targetNode.deviceName);
                        activity.connect(targetNode);
                    }
                }
            };

    private final ConnectionInfoListener myConnectionListener =
            new ConnectionInfoListener() {
                @Override
                public void onConnectionInfoAvailable(final WifiP2pInfo info) {
                    String groupOwnerAddress = info.groupOwnerAddress.getHostAddress();
                    Log.i("P2PMessenger", "(BroadcastReceiver) Group owner address: " + groupOwnerAddress);
                    // After the group negotiation, it is possible to determine the group owner
                    if (info.groupFormed && info.isGroupOwner) {
                        Log.i("P2PMessenger", "(BroadcastReceiver) Trying to create server object");
                        if (!activity.getNodeInformationObject().hasServerThread()) {
                            activity.getNodeInformationObject().setIsServer(true);
                            activity.ServerThreadStart();
                            activity.getNodeInformationObject().setHasServerThread(true);
                        }
                    } else if (info.groupFormed) {
                        Log.i("P2PMessenger", "(BroadcastReceiver) Trying to create client object");
                        activity.getNodeInformationObject().setIsServer(false);
                        activity.startClientThread(groupOwnerAddress);
                    }
                }
            };

    public WiFiDirectBroadcastReceiver(
            WifiP2pManager manager, Channel channel, MainActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi is enabled and notify appropriate activity
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    // Wifi Direct is enabled
                    if(!activity.getNodeInformationObject().isInitialized()) {
                        activity.setStatus("Waiting for keys");
                    }
                } else {
                    // Wi-Fi Direct is not enabled
                    activity.reset();
                    activity.setStatus("Wifi is disabled");
                }
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
             /*Call WifiP2pManager.requestPeers() to get a list of current peers
             request available peers from the wifi p2p manager. This is an
             asynchronous call and the calling activity is notified with a
             callback on PeerListListener.onPeersAvailable()*/
            if (manager != null) {
                manager.requestPeers(channel, myPeerListListener);
            }
            //P2P peers changed
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Respond to new connection or disconnections
            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo =
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected()) {
                // We are connected with the other device, request connection info to find group owner IP
                Toast.makeText(activity.getApplicationContext(), "Connect succeed.", Toast.LENGTH_SHORT)
                        .show();
                manager.requestConnectionInfo(channel, myConnectionListener);
                Log.i("P2PMessenger", "(BroadcastReceiver) networkInfo is connected.");

                //https://stackoverflow.com/questions/24865524/how-to-prohibit-p2p-go-to-accept-the-other-peersecond-client-connect
                WifiP2pGroup p2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                Collection<WifiP2pDevice> peerList = p2pGroup.getClientList();
                ArrayList<WifiP2pDevice> connectedPeers = new ArrayList<WifiP2pDevice>(peerList);

                Log.i("P2PMessenger", "(BroadcastReceiver) p2pGroup list size = " + connectedPeers.size());

                //New connection happened if this is true
                if(connectedPeers.size() > lastConnectedPeers.size()) {
                    //Find the device that is connected right now
                    for(WifiP2pDevice deviceAddress : lastConnectedPeers) {
                        connectedPeers.remove(deviceAddress);
                    }
                    //add it to last connected peers
                    lastConnectedPeers.addAll(connectedPeers);
                }
                //we should never have the case when updatedConnectedPeers.size() == connectedPeers.size()
                else if(connectedPeers.size() < lastConnectedPeers.size()) {
                    //cloning connectedPeers because we will apply changes to it
                    ArrayList tempConnectedPeers = new ArrayList<WifiP2pDevice>();
                    tempConnectedPeers.addAll(lastConnectedPeers);
                    //Find the device that disconnected right now
                    for(WifiP2pDevice deviceAddress : connectedPeers) {
                        tempConnectedPeers.remove(deviceAddress);
                    }
                    //Get the remaining object, that represents the one that disconnected
                    WifiP2pDevice disconnectedDevice = (WifiP2pDevice) tempConnectedPeers.get(0);

                    //
                    if(activity.getNodeInformationObject().isServer() && lastConnectedPeers.size() >= 2) {
                        Log.i("P2PMessenger", "(BroadcastReceiver) Removing client with address: " + disconnectedDevice.deviceAddress + " name: " + disconnectedDevice.deviceName);
                        activity.getServerThread().removeClient(disconnectedDevice.deviceAddress);
                    }
                    //remove it from last connected peers
                    lastConnectedPeers.remove(disconnectedDevice);
                }

                for (int i = 0; i < lastConnectedPeers.size(); i++) {
                    Log.i("P2PMessenger",
                            "(BroadcastReceiver) peer #" + i +
                                    " address: " + lastConnectedPeers.get(i).deviceAddress +
                                    " name: " + lastConnectedPeers.get(i).deviceName);
                }
            } else {
                if (activity.getNodeInformationObject().isInitialized()) {
                    if (activity.getNodeInformationObject().isServer()) {
                        Toast.makeText(activity.getApplicationContext(), "You disconnected", Toast.LENGTH_SHORT).show();
                        activity.reset();
                        activity.discover();
                    } else {
                        Toast.makeText(activity.getApplicationContext(), "Group owner disconnected", Toast.LENGTH_SHORT)
                                .show();
                        activity.reset();
                        activity.discover();
                    }
                }
                Log.i("P2PMessenger", "(BroadcastReceiver) networkInfo disconnected.");
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // Respond to this device's wifi state changing

            //Only works for Android 10 or lower.
            // For newer devices we are using the getWFDMacAddress() function found in NodeInformationObject
//            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
//            activity.getNodeInformationObject().setDeviceAddress(device.deviceAddress);
//            Log.i("P2PMessenger", "(BroadcastReceiver) This device address is: " + device.deviceAddress);
        }
    }
}
