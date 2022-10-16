package edu.utfpr.dtnmessenger;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.ceylonlabs.imageviewpopup.ImagePopup;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    //UI elements
    private TextView tvOwner, tvStatus, tvBuffer;
    private ListView listMessages;
    private Button btnNewKeyPair, btnShowMyPublicKeyQRCode, btnSetKeys, btnCopyKeyPairToClipboard;
    private EditText editTextMyKeyPair, editTextMessageText;
    private Spinner spinnerDestination;
    private Button btnAddNewContactFromQRCodeScan, btnSendMessage;

    //Wifi P2P objects
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager mManager;
    private Channel mChannel;
    private WiFiDirectBroadcastReceiver mReceiver;

    //Thread and pipes for the client's data exchange
    private PipedOutputStream poutTransmitClient = null;
    private PipedInputStream pinTransmitClient = null;
    private PipedOutputStream poutReceiveClient = null;
    private PipedInputStream pinReceiveClient = null;
    private ClientThread clientThread;

    //Thread and pipes for the server's data exchange
    private PipedOutputStream poutReceiveServer = null;
    private PipedInputStream pinReceiveServer = null;
    private PipedOutputStream poutTransmitServer = null;
    private PipedInputStream pinTransmitServer = null;
    private ServerThread serverThread;

    //Timer for apply changes in UI elements
    private Timer timerDisplay;
    private Timer timerStoreAndForwardTTL;

    //For list of messages
    public ArrayList<HashMap<String, Object>> listItem;
    public SimpleAdapter listItemAdapter;
    private MessageObject incomingMessage = new MessageObject();

    //Information about self
    private NodeInformationObject nodeInformationObject = new NodeInformationObject();

    //For QR Code popup
    ImagePopup imagePopup;
    ImageView popupImage;

    //This activity is for contact adding. It features QR code reader and a dialog to input contact name
    private ActivityResultLauncher<ScanOptions> barcodeLauncher;

    //Handlers
    Handler handlerUpdateBufferEditText;
    Handler handlerUpdateUI;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("P2PMessenger", "(MainActivity) Creating...");

        imagePopup = new ImagePopup(this);
        popupImage = new ImageView(this);
        listItem = new ArrayList<HashMap<String, Object>>();
        timerStoreAndForwardTTL = new Timer("timerStoreAndForwardTTL", true);

        //Initiating UI elements variables
        tvOwner = findViewById(R.id.tvOwner);
        tvStatus = findViewById(R.id.tvStatus);
        tvBuffer = findViewById(R.id.tvBuffer);
        listMessages = findViewById(R.id.listMessages);
        btnSetKeys = findViewById(R.id.btnSetKeys);
        btnNewKeyPair = findViewById(R.id.btnNewKeyPair);
        btnCopyKeyPairToClipboard = findViewById(R.id.btnCopyKeyPairToClipboard);
        btnShowMyPublicKeyQRCode = findViewById(R.id.btnShowMyPublicKeyQRCode);
        editTextMessageText = findViewById(R.id.editTextMessageText);
        editTextMyKeyPair = findViewById(R.id.editTextMyKeyPair);
        spinnerDestination = findViewById(R.id.spinnerDestination);
        btnSendMessage = findViewById(R.id.btnSendMessage);
        btnAddNewContactFromQRCodeScan = findViewById(R.id.btnAddNewContactFromQRCodeScan);

        //Indicates a change in the Wifi P2P status
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        //Indicates a change in the list of available peers
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        //Indicates the state of Wifi P2P connectivity has changed
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        //Indicates this device's details have changed
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        //These are the main objects used to connect to the Wifi Direct Framework
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        //More information on https://github.com/journeyapps/zxing-android-embedded
        barcodeLauncher = registerForActivityResult(new ScanContract(),
                result -> {
                    if (result.getContents() == null) {
                        //Toast.makeText(getApplicationContext(), "Cancelled", Toast.LENGTH_LONG).show();
                    } else {
                        //Create a pop up dialog if QR code scan was successful
                        //More info on https://stackoverflow.com/questions/10903754/input-text-dialog-android
                        EditText txtUrl = new EditText(this);
                        //txtUrl.setHint("");

                        new AlertDialog.Builder(this)
                                .setTitle("What's your friend's name?")
                                //.setMessage("")
                                .setView(txtUrl)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        String newContactName = txtUrl.getText().toString();
                                        nodeInformationObject.addContact(newContactName, result.getContents());
                                        updateSpinnerDestination();
                                        Toast.makeText(getApplicationContext(), newContactName + " added to contacts", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                    }
                                })
                                .show();
                    }
                });

        //This updates UI and manages the store and forward messages, cleaning up old ones based on TTL policy
        handlerUpdateBufferEditText =
                new Handler(
                        new Handler.Callback() {
                            @Override
                            public boolean handleMessage(Message msg) {
                                Calendar cal = Calendar.getInstance();
                                for (Integer key : nodeInformationObject.getStoreAndForwardMessages().keySet()) {
                                    long timestamp = nodeInformationObject.getStoreAndForwardMessages().get(key).getCreationDateUNIXTimestamp();
                                    //If message is too old, delete it and update UI
                                    if (cal.getTime().getTime() - timestamp > nodeInformationObject.getStoreAndForwardTTLPolicy()) {
                                        nodeInformationObject.getStoreAndForwardMessages().remove(key);
                                        tvBuffer.setText(String.valueOf(nodeInformationObject.getStoreAndForwardMessages().size()));
                                    }
                                }
                                return false;
                            }
                        });

        //Update UI with new messages
        handlerUpdateUI =
                new Handler(
                        new Handler.Callback() {
                            @Override
                            public boolean handleMessage(Message msg) {
                                // A type 0 message represents a normal chat message
                                if (msg.what == 0) {
                                    //If message is for this node
                                    if (incomingMessage.getMessageDestination().equals(nodeInformationObject.getMyPublicKeyString())) {
                                        if (!nodeInformationObject.hasPreviouslyReceivedThisMessage(incomingMessage)) {
                                            String sourcePubKey = incomingMessage.getMessageSource();
                                            // Get the current time (for displaying together with the message)
                                            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                                            Calendar cal = Calendar.getInstance();
                                            cal.setTime(new Date(incomingMessage.getCreationDateUNIXTimestamp()));

                                            // Fill the data into a hash map
                                            HashMap<String, Object> map = new HashMap<String, Object>();
                                            if (nodeInformationObject.getMyContacts().containsKey(getContactName(sourcePubKey))) {
                                                map.put("ItemNumber", getContactName(sourcePubKey) + " | " + dateFormat.format(cal.getTime()));
                                            } else {
                                                map.put("ItemNumber", "..." + sourcePubKey.substring(sourcePubKey.length() - 21) + " | " + dateFormat.format(cal.getTime()));
                                            }

                                            map.put("ItemMessage", incomingMessage.getMessageBody(nodeInformationObject.getMyKeyPair().getPrivate()));
                                            listItem.add(map);

                                            // Display the message
                                            listMessages.setAdapter(listItemAdapter);

                                            // Scroll to the bottom
                                            listMessages.setSelection(listMessages.getBottom());

                                            //Create a copy of incoming message and add it to node information object
                                            nodeInformationObject.addReceivedMessage(new MessageObject(incomingMessage));
                                        } else {
                                            //Already has received this message. Pass.
                                        }
                                    }
                                    //if message is for other node, store it in buffer
                                    else {
                                        nodeInformationObject.addMessageToStoreAndForward(incomingMessage);
                                        tvBuffer.setText(String.valueOf(nodeInformationObject.getStoreAndForwardMessages().size()));
                                    }
                                }
                                // A type 1 message represents a protocol message
                                else if (msg.what == 1) {
                                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                                    tvStatus.setText("Connected");
                                    tvBuffer.setText(String.valueOf(nodeInformationObject.getStoreAndForwardMessages().size()));
                                    if (nodeInformationObject.isServer()) {
                                        tvOwner.setText("Me");
                                    } else {
                                        // Display for client
                                        String sourcePubKey = incomingMessage.getMessageSource();
                                        if (nodeInformationObject.getMyContacts().containsKey(getContactName(sourcePubKey))) {
                                            tvOwner.setText(getContactName(sourcePubKey));
                                        } else {
                                            tvOwner.setText("..." + sourcePubKey.substring(sourcePubKey.length() - 21));
                                        }
                                    }
                                }
                                return false;
                            }
                        });

        //Schedule the timer that implements store-and-forward message TTL
        timerStoreAndForwardTTL.schedule(new TimerTask() {
            @Override
            public void run() {
                Message msg = new Message();
                handlerUpdateBufferEditText.sendMessage(msg);
            }
        }, 1000, 5000);

        //Initialize the adapter for the listView
        listItemAdapter = new SimpleAdapter(this,
                listItem, R.layout.list_view,
                new String[]{"ItemNumber", "ItemMessage"},
                new int[]{R.id.ItemNumber, R.id.ItemMessage});

        //UI listeners for button clicks
        createListeners();

		/*Change device name that appears to other devices. We apply the tag [P2P] in the beginning
		of the device's name. This way each device can find others that are using the app,
		filtering out other Wifi P2P devices, like TVs*/
        changeDeviceName();

        //Clear previous connections
        clearPersistentGroups();

        //Initialize and register the BroadcastReceiver
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, intentFilter);

        //------------------ Start of testing purposes only block ---------------------//
        //Impersonate someone
        //Moto X4
//        editTextMyKeyPair.setText("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC5QL7jyMYGDyIpB4oWx3TRYGRQO4Bpdg4ycIYqgSdSdTWMc+VSfWW4pZbSvBno1scoFd3GCSycJfYSGMSrEuq18UrEnBqne60UYywKQDhKXUXR2H9eybw6+eHT1qINqWuxyRac2QXKwR1r6UoFvu8N2L3ThWNMj5uW0wSixCd5ZwIDAQABMIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBALlAvuPIxgYPIikHihbHdNFgZFA7gGl2DjJwhiqBJ1J1NYxz5VJ9ZbilltK8GejWxygV3cYJLJwl9hIYxKsS6rXxSsScGqd7rRRjLApAOEpdRdHYf17JvDr54dPWog2pa7HJFpzZBcrBHWvpSgW+7w3YvdOFY0yPm5bTBKLEJ3lnAgMBAAECgYApHlRgdddyT9iat8gwPyxQLu+FGXfqsJ/9FRnKhGlIdaihmLXdZeT2o5aDBupPUqDY5mWXx8CFli10ndfFSwkaYXgdLcaRp8OMePZiBwFtBIfQE3Ennjn/vCyMsdVNb1tWxgRcmSmqEoFIccpcwAgpp9nnPjiSITMhXFC2CGNWMQJBAOXdMrXCEfEbRqGckIY6mD7Vqw1UMlWB0Eq6HLuqT39vS/D7Ppgibx8r+hd9LAhl5ZKZLCPYf3VaMnlAeAi8wjcCQQDOUQWszXek28Rd8gLQV5t4WB5b/k37UGOxx/i97FxLbXDmEbIVqTdk0QUTXiCVwnkdERLWMtzpBvaP2I7HdypRAkEAke+Fqm+0BGdUyHYmK5I35myxVJ0H99Ga9FaEt4DBSB7ZD/3zF5OFCT1aYl9N/Wb0AcbNh1SEV3UUZnbPvnxYsQJAAJEQZu0ZiwZff7KOd2wGLUpwqugD5tDNtUtLT5o6lqpySO97gbu5Pzmjve3gQQkLtBy75IK3QJyYSMTbf4jAQQJAYS0xHd6V/QcarACcfy6Ah6aBIqb7aWfTGb/Hy126RQf5FOyOqYx/aC9wxpWzDm076qqR+2RSjrmGUMGBiWGBPw==");
//        nodeInformationObject.addContact("Moto G6", "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHtq7pa2tj5ZlIC2Yobl6HTfRpQmP63IMQRemlov8liSr0yErEDGCXC701P3FYmpGIXD1HYm9VS7LskkIp7AKPbqTdoi9E8FOFuU5OKRxgCRIl7UMmwtqPe8Z5ORJntEDtPcK0v6mBiwLwkn7oCc+grJSn5CbWCprSmq59w+ep0wIDAQAB");
//        nodeInformationObject.addContact("Galaxy S20", "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCoTVvat3wbcCywQ4MfcUUpVSIhOXqTN2d7UlUYxaP4g1cRXzDVnK89zfgWp6GqtVbNCcXuQu+ASFU4b8f+nCHP5aAvtKZX3UCJpaBJrJovgT2khWFVW7tt1NHpF/Bk0q3JEvRFTluGa8C1Lq0GjowzWJIRqikFVFiLw2N8hYjI0QIDAQAB");

        //Moto G6
//        editTextMyKeyPair.setText("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHtq7pa2tj5ZlIC2Yobl6HTfRpQmP63IMQRemlov8liSr0yErEDGCXC701P3FYmpGIXD1HYm9VS7LskkIp7AKPbqTdoi9E8FOFuU5OKRxgCRIl7UMmwtqPe8Z5ORJntEDtPcK0v6mBiwLwkn7oCc+grJSn5CbWCprSmq59w+ep0wIDAQABMIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAMe2rulra2PlmUgLZihuXodN9GlCY/rcgxBF6aWi/yWJKvTISsQMYJcLvTU/cViakYhcPUdib1VLsuySQinsAo9upN2iL0TwU4W5Tk4pHGAJEiXtQybC2o97xnk5Eme0QO09wrS/qYGLAvCSfugJz6CslKfkJtYKmtKarn3D56nTAgMBAAECgYBYrE20OSw48ighO2J0ADE7fUrQc5hluYP99TS9BQapeWJXxhigPGikmCM94bnnX9XPSDkzGUsagZ5jeLsk1vP5RqGxZpTxoDgEztdpcU0B0fSMUFQQ5H2xWLv7e7ysSYAQpmTfyoNbjHx0Zq+2JUS56Un3si4Gw1daaXH3iEIn+QJBAPl22AkEFaIr1DeYM8YHppM9EzqpSbsegiMSJXyYGpIUSDGqzFsB8q89Lwr2r8qG18VCx+SmARjTLunRa/9MpLkCQQDM8il5ElaXCvJXtGuTJKxy5SGBWj/pWLf7pxAJNb62+KUCDPP40dtbHETpwMpZ0b7wp77AD6C7MqcICEMOHhTrAkEA3htv8WcPk9oOEomS3ygEqWdhbYM4QD/DglIvyiTq01D3jjERzZ2IY3nIHqzQizNPfTQeIXej1mSAinGJBD9LEQJAIEdbxNfgj3WH6cxezRQPnSD9f/QI8OWqRJZxbiHq4cKTqpkDrALRe64eJHra4/6nBhxFbNaJSDKYICm89fJC9QJAT++TigqwfuFsD2G4mOFMw6s6Hp5RTIeGi644ygfS1BmPXlXvZYxHDFe1ft6yL0P5HNpEnkW4RfKMShJrPTMXCw==");
//        nodeInformationObject.addContact("Moto X4", "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC5QL7jyMYGDyIpB4oWx3TRYGRQO4Bpdg4ycIYqgSdSdTWMc+VSfWW4pZbSvBno1scoFd3GCSycJfYSGMSrEuq18UrEnBqne60UYywKQDhKXUXR2H9eybw6+eHT1qINqWuxyRac2QXKwR1r6UoFvu8N2L3ThWNMj5uW0wSixCd5ZwIDAQAB");
//        nodeInformationObject.addContact("Galaxy S20", "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCoTVvat3wbcCywQ4MfcUUpVSIhOXqTN2d7UlUYxaP4g1cRXzDVnK89zfgWp6GqtVbNCcXuQu+ASFU4b8f+nCHP5aAvtKZX3UCJpaBJrJovgT2khWFVW7tt1NHpF/Bk0q3JEvRFTluGa8C1Lq0GjowzWJIRqikFVFiLw2N8hYjI0QIDAQAB");

        //Galaxy S20
        editTextMyKeyPair.setText("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCoTVvat3wbcCywQ4MfcUUpVSIhOXqTN2d7UlUYxaP4g1cRXzDVnK89zfgWp6GqtVbNCcXuQu+ASFU4b8f+nCHP5aAvtKZX3UCJpaBJrJovgT2khWFVW7tt1NHpF/Bk0q3JEvRFTluGa8C1Lq0GjowzWJIRqikFVFiLw2N8hYjI0QIDAQABMIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAKhNW9q3fBtwLLBDgx9xRSlVIiE5epM3Z3tSVRjFo/iDVxFfMNWcrz3N+Banoaq1Vs0Jxe5C74BIVThvx/6cIc/loC+0plfdQImloEmsmi+BPaSFYVVbu23U0ekX8GTSrckS9EVOW4ZrwLUurQaOjDNYkhGqKQVUWIvDY3yFiMjRAgMBAAECgYABZsmDyADg0p2QpzNFlQIkLrkOyUV/ydeTA5PBVc4AgQl908rakIg2DcvJ1oIGY6WyJelvyz9m1FNfWbDy6hZu+w2VYslDthnoTyxsI8lCgNxp4gXFYREORdTh9WA666yyP4K6TIu455scguqsbWqwFc0u0aTIs0/MPKyasdMq5QJBAN66cpnR2TLZfEMatlsvIXeCxe2z/3C0qeZwe5voUKYga2kos/9yTg64vRxr20eti8i67YyRKp47VUJWZ1QdVpUCQQDBcY2N6yEHsY+EL9JPtMyLeCVWDRLqeHmijSaIQaLvomS4OxAu4ukrax/QBxOLxTTCfhOpcqK6kjmu+mGRpUZNAkBzHUo91ge8EEv5IsU9O47AhgZmZLGRPs7RGzHH0rpcIkVhhHgDfsB5O2ICXnxm/3tPs80y6ZRtU50tPBsLjl5BAkEArud1DfSjEMnC8corZlYa+5/OYle/2rDDie4GAP0XzYQPfWQp5brVCKT7RqSfT6knYxOLw5IbAIZmEYfh4EaHoQJAdUeDfzTwEu6K3K9m9msstwUpqaRCLKL+fQpComDb5XRoT3qXCVh/2zlG7kVG24nY1/uhD4nJVdF4ppibsJ0WWA==");
        nodeInformationObject.addContact("Moto X4", "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC5QL7jyMYGDyIpB4oWx3TRYGRQO4Bpdg4ycIYqgSdSdTWMc+VSfWW4pZbSvBno1scoFd3GCSycJfYSGMSrEuq18UrEnBqne60UYywKQDhKXUXR2H9eybw6+eHT1qINqWuxyRac2QXKwR1r6UoFvu8N2L3ThWNMj5uW0wSixCd5ZwIDAQAB");
        nodeInformationObject.addContact("Moto G6", "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHtq7pa2tj5ZlIC2Yobl6HTfRpQmP63IMQRemlov8liSr0yErEDGCXC701P3FYmpGIXD1HYm9VS7LskkIp7AKPbqTdoi9E8FOFuU5OKRxgCRIl7UMmwtqPe8Z5ORJntEDtPcK0v6mBiwLwkn7oCc+grJSn5CbWCprSmq59w+ep0wIDAQAB");

        updateSpinnerDestination();
        //------------------ End of testing purposes only block ---------------------//
    }

    //Register the BroadcastReceiver with the intent values to be matched
    @Override
    public void onResume() {
        super.onResume();
        Log.i("P2PMessenger", "(MainActivity) Resuming...");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i("P2PMessenger", "(MainActivity) Pausing...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.i("P2PMessenger", "(MainActivity) Destroying...");
        unregisterReceiver(mReceiver);
        //Stop the timer
        timerStoreAndForwardTTL.cancel();

        //Close all pipes and threads
        reset();

        //Cancel all on-going P2P connections
        mManager.cancelConnect(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.i("P2PMessenger", "(MainActivity) Connection cancelled successfully!");
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.i("P2PMessenger", "(MainActivity) Connection cancelled successfully!");
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("P2PMessenger", "(MainActivity) Stopping...");
    }

    // Method called to close threads and pipes in order for the app to be able to start over on a disconnect event
    public void reset() {
        //Clear information stored in nodeInformationObject
        nodeInformationObject.resetConnection();

        //Reset the UI
        tvOwner.setText("---");

        //Stop the timer
        if (timerDisplay != null) {
            timerDisplay.cancel();
        }

        //Close the opened threads
        if (clientThread != null) {
            clientThread.interrupt();
            clientThread = null;
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }

        //Close the opened streams
        if (poutTransmitClient != null) {
            try {
                poutTransmitClient.close();
            } catch (IOException e) {
                //Catch logic
            }
        }
        if (pinTransmitClient != null) {
            try {
                pinTransmitClient.close();
            } catch (IOException e) {
                //Catch logic
            }
        }
        if (poutReceiveClient != null) {
            try {
                poutReceiveClient.close();
            } catch (IOException e) {
                //Catch logic
            }
        }
        if (pinReceiveClient != null) {
            try {
                pinReceiveClient.close();
            } catch (IOException e) {
                //Catch logic
            }
        }
        if (poutTransmitServer != null) {
            try {
                poutTransmitServer.close();
            } catch (IOException e) {
                //Catch logic
            }
        }
        if (pinTransmitServer != null) {
            try {
                pinTransmitServer.close();
            } catch (IOException e) {
                //Catch logic
            }
        }
        if (poutReceiveServer != null) {
            try {
                poutReceiveServer.close();
            } catch (IOException e) {
                //Catch logic
            }
        }
        if (pinReceiveServer != null) {
            try {
                pinReceiveServer.close();
            } catch (IOException e) {
                //Catch logic
            }
        }
    }

    //Method for other objects outside the MainActivity to update the UI status
    public void setStatus(String status) {
        tvStatus.setText(status);
    }

    //Changing how the device appears for others during the discovery method
    public void changeDeviceName() {
        //How to WifiP2pDevice.deviceName for current device?
        //https://stackoverflow.com/questions/53607516/how-to-wifip2pdevice-devicename-for-current-device
        //Only works for Android 10 or lower.
        // If using Android 11 or newer please change Wifi Direct device name to [P2P] YOUR_CUSTOM_NAME
        try {
            Method method = mManager.getClass().getMethod("setDeviceName", WifiP2pManager.Channel.class, String.class, WifiP2pManager.ActionListener.class);
            method.invoke(mManager, mChannel, "[P2P] " + android.os.Build.MODEL, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i("P2PMessenger", "(MainActivity) Name successfully changed");
                }

                @Override
                public void onFailure(int reason) {
                    Log.i("P2PMessenger", "(MainActivity) Name change failed: " + reason);
                }
            });
        } catch (Exception e) {
            //Catch logic
        }
    }

    /*Clear known connections in order to prevent malfunctions (this cause app to ask for permission
    everytime it connects to someone, even when it was already previously connected to it*/
    private void clearPersistentGroups() {
        //https://stackoverflow.com/questions/15152817/can-i-change-the-group-owner-in-a-persistent-group-in-wi-fi-direct
        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals("deletePersistentGroup")) {
                    // Delete any persistent group
                    for (int netid = 0; netid < 32; netid++) {
                        methods[i].invoke(mManager, mChannel, netid, null);
                    }
                    Log.i("P2PMessenger", "(MainActivity) Deleted persistent groups");
//					Toast.makeText(getApplicationContext(), "Deleted groups", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Fetch contact name based on its public key
    private String getContactName(String publicKey) {
        for (String key : nodeInformationObject.getMyContacts().keySet()) {
            if (nodeInformationObject.getMyContacts().get(key).equals(publicKey)) {
                return key;
            }
        }
        return null;
    }

    //Get object containing information about self
    public NodeInformationObject getNodeInformationObject() {
        return nodeInformationObject;
    }

    //Discover peers
    public void discover() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.i("P2PMessenger", "(MainActivity) Discovery Initiated!");
                tvStatus.setText("Discovery");
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.i("P2PMessenger", "(MainActivity) Discovery Failed!");
                tvStatus.setText("Discovery failed");
            }
        });
    }

    //Get a peer and connect
    public void connect(WifiP2pDevice device) {
        //Configuring the object used to connect
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.groupOwnerIntent = 0;
        config.wps.setup = WpsInfo.PBC;

        //Connecting
        mManager.connect(mChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                //Set IsConnected
                nodeInformationObject.setIsConnected(true);
                Log.i("P2PMessenger", "(MainActivity) mManager onSuccess called");
                Log.i("P2PMessenger", "(MainActivity) Connecting to address: " + device.deviceAddress + " name: " + device.deviceName);
                tvStatus.setText("Connecting");

                //Enable UI elements
                spinnerDestination.setEnabled(true);
                editTextMessageText.setEnabled(true);
                btnSendMessage.setEnabled(true);
            }

            @Override
            public void onFailure(int reason) {
                Log.i("P2PMessenger", "(MainActivity) mManager onFailure called");
            }
        });
    }

    //Create pipes between UI thread and client thread
    public void startClientThread(String host) {
        Log.i("P2PMessenger", "(MainActivity) Initiating client thread");
        try {
            timerDisplay = new Timer("timerDisplay", true);
            poutTransmitClient = new PipedOutputStream();
            pinTransmitClient = new PipedInputStream(poutTransmitClient);
            poutReceiveClient = new PipedOutputStream();
            pinReceiveClient = new PipedInputStream(poutReceiveClient);

            clientThread = new ClientThread(host, poutReceiveClient, pinTransmitClient, nodeInformationObject);
            clientThread.setDaemon(true);
            clientThread.start();

            //Schedule the timer
            timerDisplay.schedule(new TimerTask() {
                @Override
                public void run() {
                    //Get message type
                    int msgType = receiveMessage();
                    //Notify the handler of the message
                    Message m = new Message();
                    m.what = msgType;
                    handlerUpdateUI.sendMessage(m);
                }
            }, 1000, 500);
        } catch (IOException e) {
            //Catch logic
        }
    }

    //Create pipes between UI thread and server thread
    public void ServerThreadStart() {
        Log.i("P2PMessenger", "(MainActivity) Initiating server thread");
        try {
            timerDisplay = new Timer("timerDisplay", true);
            poutReceiveServer = new PipedOutputStream();
            pinReceiveServer = new PipedInputStream(poutReceiveServer);
            poutTransmitServer = new PipedOutputStream();
            pinTransmitServer = new PipedInputStream(poutTransmitServer);

            serverThread = new ServerThread(poutReceiveServer, pinTransmitServer, nodeInformationObject);
            serverThread.setDaemon(true);
            serverThread.start();

            //Schedule the timer
            timerDisplay.schedule(new TimerTask() {
                @Override
                public void run() {
                    //Get message type
                    int msgType = receiveMessage();
                    //Notify the handler of the message
                    Message m = new Message();
                    m.what = msgType;
                    handlerUpdateUI.sendMessage(m);
                }
            }, 1000, 500);
        } catch (IOException e) {
            //Catch logic
        }
    }

    public ServerThread getServerThread() {
        return this.serverThread;
    }

    //Receive a new message, returns the type of the received message
    public int receiveMessage() {
        //Pipe that will be instantiated based on the node role (server/client)
        PipedInputStream pinRcv = null;

        try {
            //Instantiating the pipe
            if (nodeInformationObject.isServer()) {
                pinRcv = this.pinReceiveServer;
            } else {
                pinRcv = this.pinReceiveClient;
            }

            if (pinRcv.available() <= 0) {
                //No message yet
                return -1;
            }
            //A message was received, let's read it
            MessageObject messageObject = new MessageObject();
            messageObject.readMessage(pinRcv);
            this.incomingMessage = messageObject;

            Log.i("P2PMessenger", "(MainActivity) Message received from: " + messageObject.getMessageSource());
            Log.i("P2PMessenger", "(MainActivity) Message type: " + messageObject.getMessageType());
        } catch (IOException e) {
            //Catch logic
            return -2;
        }

        return this.incomingMessage.getMessageType();
    }

    //Update UI element with current contacts
    private void updateSpinnerDestination() {
        String[] items = nodeInformationObject.getMyContacts().keySet().toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        spinnerDestination.setAdapter(adapter);
    }

    //Create the listeners for the UI elements
    private void createListeners() {
        //Generate new key pair on button click
        btnNewKeyPair.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        editTextMyKeyPair.setText(Cryptography.generateKeyPairString());
                    }
                });

        //Set the key pair that was inputted, and start discovery
        btnSetKeys.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String myKeyPairString = editTextMyKeyPair.getText().toString();
                        nodeInformationObject.setMyKeyPair(Cryptography.getKeyPairFromKeyPairString(myKeyPairString));

                        //Disable UI elements for blocking the user from changing its seed during app execution
                        btnSetKeys.setEnabled(false);
                        btnNewKeyPair.setEnabled(false);
                        editTextMyKeyPair.setEnabled(false);
                        btnShowMyPublicKeyQRCode.setEnabled(true);
                        btnCopyKeyPairToClipboard.setEnabled(true);

                        //Start the peers discovery
                        discover();
                        Toast.makeText(getApplicationContext(), "Please remember to save your key pair offline",
                                Toast.LENGTH_LONG).show();
                    }
                });

        //Show public key QR code for friends to be able to add it
        btnShowMyPublicKeyQRCode.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        MultiFormatWriter writer = new MultiFormatWriter();
                        try {
                            BitMatrix matrix = writer.encode(nodeInformationObject.getMyPublicKeyString(), BarcodeFormat.QR_CODE, 350, 350);
                            BarcodeEncoder encoder = new BarcodeEncoder();
                            Bitmap bitmap = encoder.createBitmap(matrix);
                            popupImage.setImageBitmap(bitmap);
                            InputMethodManager manager = (InputMethodManager) getSystemService(
                                    Context.INPUT_METHOD_SERVICE
                            );
                            manager.hideSoftInputFromWindow(editTextMyKeyPair.getApplicationWindowToken(), 0);
                            imagePopup.initiatePopup(popupImage.getDrawable());
                            imagePopup.viewPopup();
                            Toast.makeText(getApplicationContext(), "Show this QR Code to your friends",
                                    Toast.LENGTH_SHORT).show();
                        } catch (WriterException e) {
                            e.printStackTrace();
                        }
                    }
                });

        //Copy key pair to clipboard in order to save it offline
        btnCopyKeyPairToClipboard.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("My key pair", editTextMyKeyPair.getText().toString());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getApplicationContext(), "Copied to clipboard",
                                Toast.LENGTH_SHORT).show();
                    }
                });

        //Listens to changes in the key pair edit text UI element, for enabling buttons when necessary
        editTextMyKeyPair.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSetKeys.setEnabled(s.toString().trim().length() != 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        //Start QR code scan routine
        btnAddNewContactFromQRCodeScan.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ScanOptions options = new ScanOptions();
                        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
                        options.setPrompt("Scan a friend's QR Code");
                        options.setCameraId(0);  // Use a specific camera of the device
                        options.setBeepEnabled(false);
                        options.setBarcodeImageEnabled(true);
                        barcodeLauncher.launch(options);
                    }
                });

        //Send a message to a contact
        btnSendMessage.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Get the content from the destination input text]
                        if (nodeInformationObject.getMyContacts().keySet().size() != 0) {
                            String selectedContact = spinnerDestination.getSelectedItem().toString();
                            String destinationPubKeyString = nodeInformationObject.getMyContacts().get(selectedContact);
                            if (destinationPubKeyString.length() == 0) {
                                Toast.makeText(getApplicationContext(), "Destination empty!", Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }

                            // Get the content from the message body input text
                            String message = editTextMessageText.getText().toString();
                            if (message.length() == 0) {
                                Toast.makeText(getApplicationContext(), "Message empty!", Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }

                            if (!nodeInformationObject.isInitialized()) {
                                Toast.makeText(getApplicationContext(), "Please enter your key pair or generate a new one first", Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }

                            // Send the message
                            MessageObject messageToBeTransmitted = new MessageObject();
                            messageToBeTransmitted.setMessageType(0);
                            messageToBeTransmitted.setMessageSource(nodeInformationObject.getMyPublicKeyString());
                            messageToBeTransmitted.setMessageDestination(destinationPubKeyString);
                            messageToBeTransmitted.setMessageBody(message);
                            if (nodeInformationObject.isConnected()) {
                                if (nodeInformationObject.isServer()) {
                                    messageToBeTransmitted.sendMessage(poutTransmitServer);
                                } else {
                                    messageToBeTransmitted.sendMessage(poutTransmitClient);
                                }
                                nodeInformationObject.addTransmittedMessage(messageToBeTransmitted);
                            } else {
                                nodeInformationObject.addMessageToStoreAndForward(messageToBeTransmitted);
                                tvBuffer.setText(String.valueOf(nodeInformationObject.getStoreAndForwardMessages().size()));
                            }

                            // Clear the input text
                            editTextMessageText.setText("");

                            // Get the current time (for displaying together with the message)
                            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                            long currDate = messageToBeTransmitted.getCreationDateUNIXTimestamp();

                            // Fill the data into a hash map
                            HashMap<String, Object> map = new HashMap<String, Object>();
                            map.put(
                                    "ItemNumber",
                                    "Me to "
                                            + getContactName(destinationPubKeyString)
                                            + " "
                                            + dateFormat.format(currDate));

                            map.put("ItemMessage", message);
                            listItem.add(map);

                            // Display the message
                            listMessages.setAdapter(listItemAdapter);

                            // Scroll list to the bottom
                            listMessages.setSelection(listMessages.getBottom());
                        } else {
                            Toast.makeText(getApplicationContext(), "Please add a contact first", Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
    }
}