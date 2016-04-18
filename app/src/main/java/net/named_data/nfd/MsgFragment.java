package net.named_data.nfd;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.KeyType;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.util.Blob;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

/**
 * Created by yuan on 16-4-15.
 */
public class MsgFragment extends Fragment {
    public final static int OLDMSG = 0;
    public final static int NEWMSG = 1;
    public final static int DISCONNECT = 2;
    public static final int MAXLEN = 5000;
    public static final String PREFIX = "/ndn/org/test/MSG";
    public static final int INITIATIVE = 0;
    public static final int PASSIVE = 1;
    private final String TAG = "NDN";

    private ListView m_List;
    private ChatLIstViewAdapter m_adapter2;
    private ArrayList<HashMap<String, Object>> m_chatContent2 = new ArrayList<HashMap<String, Object>>();
    private EditText m_input;
    private Button m_sendBtn;
    private EditText m_addrEdit;
    private Switch m_startSwitch;
    private String m_faceAddr = new String("");
    private NetThread m_netThread = null;
    private boolean m_isConnect;
    private boolean m_isChecked = false;
    private int m_falseCount = 0;
    private ReceiveThread m_recvThread;
    private String m_ipAddr;

    private boolean m_startPassive = false;
    private boolean m_endPassive = false;


    public static Fragment newInstance() {
        return new MsgFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "OnCreate!");
        /***
         * aquire the ip address of the android phone
         */
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface inf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = inf.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        m_ipAddr = inetAddress.getHostAddress().toString();

                    }
                }

            }
        } catch (SocketException e) {
            Log.e(TAG, "Fail to get the IP address of the phone.");
        }
        Log.i(TAG, "m_ipAddr=" + m_ipAddr);

        if (m_ipAddr != null) {
            m_recvThread = new ReceiveThread();
            m_recvThread.start();
        } else showToast("Please connect to WIFI first!");

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        View v = inflater.inflate(R.layout.activity_chat, null);

//        m_rootLayout = (LinearLayout) v.findViewById(R.id.root);
//        m_chatLayout = (LinearLayout) v.findViewById(R.id.topPanel);
        m_List = (ListView) v.findViewById(R.id.listView1);
        m_adapter2 = new ChatLIstViewAdapter(getActivity(), m_chatContent2);
        m_List.setAdapter(m_adapter2);

        m_addrEdit = (EditText) v.findViewById(R.id.ed_faceAddr);
        m_input = (EditText) v.findViewById(R.id.inputEdit);
        m_sendBtn = (Button) v.findViewById(R.id.sendBtn);
        m_sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = m_input.getText().toString().trim();
                if (msg.length() == 0) {
                    showToast("The message can't be empty!");
                    return;
                }
                if (msg.length() > MAXLEN) {
                    showToast("The length of message is too long!");
                    return;
                }
                if (!m_faceAddr.equals("")) {
                    Log.i(TAG, "Ready to send new message: " + msg);
                    try {
                        m_netThread.setReady(URLEncoder.encode(msg, "UTF-8"), NEWMSG);
                        //sendMsg(200, msg);//200: showOwnMessage(msg);
                    } catch (UnsupportedEncodingException e) {
                        Log.i(TAG, "UnsupportedEncoding in sendmsg");
                    }
                } else {
                    showToast("Please input IP address and connect first!");
                    return;
                }
            }
        });

        m_startSwitch = (Switch) v.findViewById(R.id.switch_chatStart);
//        m_startSwitch.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                m_isChecked = !m_isChecked;
//                Log.i(TAG, "isChecked=" + m_isChecked);
//                if (m_isChecked) {  //start to chat
////                    Log.i(TAG, "targetFace: " + m_targetFace);
////                    if (m_targetFace.equals("")) {
////                        String tmp = m_addrEdit.getText().toString();
////                        if (!tmp.equals("")) {
////                            connect(tmp);
////                        }
////
//                    String tmp = m_addrEdit.getText().toString();
//                    if (!tmp.equals("")) {
//                        m_faceAddr = tmp;
//                        Log.i(TAG, "m_faceAddr: " + m_faceAddr);
//                        m_startSwitch.setChecked(m_isChecked);
//                        connect(INITIATIVE);
//                    } else {
//                        showToast("Face address can't be empty!\nPlease input the face address!");
//                    }
//                } else {  //stop to chat
//                    m_addrEdit.setText("");
//                    m_netThread.disconnect();
//                }
//            }
//        });

        m_startSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                Log.i(TAG, "isChecked=" + isChecked);
//                if (isChecked) {  //start to chat
////                    Log.i(TAG, "targetFace: " + m_targetFace);
////                    if (m_targetFace.equals("")) {
////                        String tmp = m_addrEdit.getText().toString();
////                        if (!tmp.equals("")) {
////                            connect(tmp);
////                        }
////                    }
//                    if (m_isTouch) {
//                        m_isTouch = false;
//                        m_faceAddr = m_addrEdit.getText().toString();
//                        connect(INITIATIVE);
//                    } else {
//                        connect(PASSIVE);
////                        m_faceAddr = m_targetFace;
////                        m_netThread = new NetThread(1);
////                        m_netThread.start();
//                    }
//                } else {  //stop to chat
//                    if (m_isTouch) {
//                        m_isTouch = false;
//                        m_addrEdit.setText("");
//                        m_netThread.disconnect();
//                    } else {
//                        m_isConnect = false;
//                    }
////                    disconnect("Shut down the connect");
//                }
                if (isChecked && !m_startPassive) {
                    String tmp = m_addrEdit.getText().toString();
                    if (!tmp.equals("")) {
                        m_faceAddr = tmp;
                        Log.i(TAG, "m_faceAddr: " + m_faceAddr);
                        m_startSwitch.setChecked(isChecked);
                        connect(INITIATIVE);
                    } else showToast("Face address can't be empty!\nPlease input the face address!");
                } else if (!isChecked && !m_endPassive) {
                    m_startSwitch.setChecked(isChecked);
                    m_addrEdit.setText("");
                    m_netThread.disconnect();
                }
            }
        });
        return v;
    }

    public void connect(int type) {
        m_isConnect = true;
        m_netThread = new NetThread(type);
        m_netThread.start();
    }

//    public void fail2Connect(String str) {
//        m_isConnect = false;
//        sendMsg(403, str);
//    }

    private class PingTimer implements OnData, OnTimeout {
        /***
         * The operations on the m_falseCount and callbackCount_ is important!
         */
        private long startTime;

        public void onData(Interest interest, Data data) {
            ++callbackCount_;
            m_falseCount = 0;
            Log.i(TAG, "onData: m_falseCount = " + m_falseCount);
            Log.i(TAG, "Got data packet with name " + data.getName().toUri());
            long elapsedTime = System.currentTimeMillis() - this.startTime;


            //DEBUG
            String name = data.getName().toUri();
            String pingTarget = name.substring(0, name.lastIndexOf("/"));
            String contentStr = pingTarget + ": " + String.valueOf(elapsedTime) + " ms";
            Log.i(TAG, ">>Content: " + contentStr);
//            // Send a result to Screen
//            Message msg = new Message();
//            msg.what = 200; // Result Code ex) Success code: 200 , Fail Code:
//            // 400 ...
//            msg.obj = contentStr; // Result Object
//            m_handler.sendMessage(msg);
//
            if (isFirstConnect(interest.getName().toUri())) {
                sendMsg(100, "Successfully connect to " + m_faceAddr);
//                m_targetFace = m_faceAddr;
//                showMessage("Successfull connect to " + m_faceAddr + ". Now let's chat!",
//                        ChatLIstViewAdapter.ROLE_TARGET);
            } else if (isDisconnect(interest.getName().toUri())) {
                //m_isConnect = false;
                //m_netThread.netFaceClose();
                sendMsg(400, "Successfully disconnect!");
            }
            else
                sendMsg(200, getContent(interest.getName().toUri()));
        }

        private String getContent(String msg) {
            String[] strs = msg.split("/");
            for (int i = 0; i < strs.length; ++i)
                Log.i(TAG, "[" + i + "]" + strs[i]);

            int i = 0;
            for (; i < strs.length; ++i)
                if (strs[i].equals("MSG"))
                    break;
            Log.i(TAG, "onInterest: i=" + i + " length=" + strs.length);
            if (i + 4 <= strs.length) {i += 4;

                try {
                    return URLDecoder.decode(strs[i], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.i(TAG, "UnsupportedEncoding in recvmsg");
                }
            }

            return null;
        }

        public int callbackCount_ = 0;

        public void onTimeout(Interest interest) {
            ++callbackCount_;
            ++m_falseCount;
//            Message msg = new Message();
//            msg.what = 400; // 400 ...
            String contentStr = interest.getName().toUri();
            Log.i(TAG, "OnTimeOut: Time out for interest " + contentStr);
//            msg.obj = contentStr; // Result Object
//            m_handler.sendMessage(msg);
//            Log.i(TAG, contentStr);

            /***
             * Since the connect is based on the protocol TCP/IP, if it is time out to get the data
             * packet from the target for more than 3 times, we can conclude that the connect has been broken.
             */
            Log.i(TAG, "m_falseCount = " + m_falseCount);
            if (m_falseCount >= 3) {
//                fail2Connect("Failed to send the message.\n Please connect again!");
                sendMsg(401, "Failed to send the message.\n Please connect again!");
            } else {  //resend the message
                m_netThread.setReady(contentStr, OLDMSG);
            }
        }

        public void startUp() {
            startTime = System.currentTimeMillis();
            callbackCount_ = 0;
        }

    }

    public boolean isFirstConnect(String interest) {
        String[] strs = interest.split("/");
        int len = strs.length;
        for (int i = 0; i < len; i++)
            if (strs[i].equals("MSG") && strs[i + 1].equals("0") && strs[len - 1].equals("CONNECT")) {
                Log.i(TAG, "isFirstConnect!");
                return true;
            }
        return false;
    }

    public boolean isDisconnect(String interest) {
        String[] strs = interest.split("/");
        int len = strs.length;
        for (int i = 0; i < len; i++)
            if (strs[i].equals("MSG") && strs[i + 1].equals("0") && strs[len - 1].equals("DISCONNECT")) {
                Log.i(TAG, "isDisconnect!");
                return true;
            }
        return false;
    }

    private class NetThread extends Thread {

        private final int type;

        /***
         * The constructor of the class NetThread.
         *
         * @param type:0, the NetThread initiativly connect to others, else it is passively connected to.
         */
        public NetThread(int type) {
            m_startPassive = false;
            this.type = type;
            isReady = false;
            netFace = new Face(m_faceAddr);
            Log.i(TAG, "NetThread faceAddr: " + m_faceAddr);
        }

        @Override
        public void run() {
            try {
                PingTimer timer = new PingTimer();
                String pingName;

                if (type == INITIATIVE) {
                    timer.startUp();
                    pingName = PREFIX + "/0/" + System.currentTimeMillis() + "/" + m_ipAddr;
                    Name name = new Name(pingName);
                    name.append("CONNECT");
                    pendingInterestId = netFace.expressInterest(name, timer, timer);
                    Log.i(TAG, "Express name " + name.toUri());
                    while (timer.callbackCount_ < 1) {
                        netFace.processEvents();
                        Thread.sleep(5);
                    }
                    netFace.removePendingInterest(pendingInterestId);
                }

                while (m_isConnect) {
                    if (isReady) {
                        isReady = false;
                        Log.i(TAG, "isResend=" + isResend);
                        if (isResend == OLDMSG) {
                            pingName = content;
                            Log.i(TAG, "oldmsg: " + pingName);
                        }
                        else if (isResend == NEWMSG) { //common msg: remember it is 1
                            pingName = PREFIX + "/1/" + System.currentTimeMillis() + "/" + m_ipAddr + "/" + content;
                            Log.i(TAG, "newmsg: " + pingName);
                        }
                        else { //DISCONNECT: remember it is 0
                            pingName = PREFIX + "/0/" + System.currentTimeMillis() + "/" + m_ipAddr + "/" + content;
                            Log.i(TAG, "disconnect msg: " + pingName);
                        }
                        Name msgName = new Name(pingName);
//                        Log.i(TAG, "isResend =" + isResend);
                        Log.i(TAG, "Express name " + msgName.toUri());
                        timer.startUp();
                        pendingInterestId = netFace.expressInterest(msgName, timer, timer);
                        while (timer.callbackCount_ < 1) {
                            netFace.processEvents();
                            Thread.sleep(5);
                        }

                        netFace.removePendingInterest(pendingInterestId);
                    }
                }
//                netFaceClose();
            } catch (Exception e) {
                Log.i(TAG, "exception: " + e.getMessage());
//                netFaceClose();
//                disconnect("Connect Failed! Please connect again!");
                sendMsg(401, "Exception: " + e.getMessage());
            }
        }

        public void netFaceClose() {
            m_endPassive = false;
            m_faceAddr = "";
            netFace.shutdown();
            Log.i(TAG, "netFaceClose!");
        }

        public void setReady(String msg, int type) {
            content = msg;
            isResend = type;
            isReady = true;
        }

        public void disconnect() {
            content = "DISCONNECT";
            isResend = DISCONNECT;
            isReady = true;
        }

        private int isResend = -1;
        private Face netFace;
        private long pendingInterestId;
        private String content = null;
        private boolean isReady;
    }

    private class Echo implements OnInterestCallback, OnRegisterFailed {

        private final String[] strBuf;
        private int pos;
        private final int bufSize = 6;

        public Echo(KeyChain keyChain, Name certificateName) {
            keyChain_ = keyChain;
            certificateName_ = certificateName;

            strBuf = new String[bufSize];
            for (int i = 0; i < bufSize; i++)
                strBuf[i] = new String();
            pos = 0;
        }

        public void
        onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                   InterestFilter filter) {
            //++responseCount_;

            // Make and sign a Data packet.
            Data data = new Data(interest.getName());
            String content = "<<Echo " + interest.getName().toString();
            data.setContent(new Blob(content));
            //Log.i(TAG, content);

            /***
             * Cache for the interest. Its operation is like the operation on the queue.
             * If it receives the same interest, it won't response to the interest, to avoid to display the same msg.
             */
            String tmpContent = interest.getName().toUri();
            boolean hasDisplay = false;
            for (int i = 0; i < bufSize; i++)
                if (tmpContent.equals(strBuf[i])) {
                    hasDisplay = true;
                    break;
                }
            if (!hasDisplay) {
                strBuf[pos] = tmpContent;
                pos = (pos + 1) % bufSize;
                Log.i(TAG, "pos = " + pos);
            }

            //DEBUG
            String[] strs = interest.getName().toString().split("/");
            for (int i = 0; i < strs.length; ++i)
                Log.i(TAG, "[" + i + "]" + strs[i]);

            int i = 0;
            for (; i < strs.length; ++i)
                if (strs[i].equals("MSG"))
                    break;
            Log.i(TAG, "onInterest: i=" + i + " length=" + strs.length);
            if (i + 4 <= strs.length) {
                i += 4;
                /***
                 * Just response to the target face.
                 */
                Log.i(TAG, "m_faceAddr=" + m_faceAddr);
                if (m_faceAddr.equals(strs[i - 1]) || m_faceAddr.equals("")) {
                    Log.i(TAG, "Receive msg from: " + strs[i - 1]);

                    if (!hasDisplay) {
                        if (isFirstConnect(tmpContent)) {
//                            m_faceAddr = strs[i - 1];
                            sendMsg(300, strs[i - 1]);  //300: someone is trying to connect with you
                        } else if (isDisconnect(tmpContent)) {
                            sendMsg(401, "The other side has disconnected!");
                        } else {
                            try {
                                sendMsg(201, URLDecoder.decode(strs[i], "UTF-8"));//200: showOwnMessage(msg);
                            } catch (UnsupportedEncodingException e) {
                                Log.i(TAG, "UnsupportedEncoding in recvmsg");
                            }
                        }
                    }

                    try {
                        keyChain_.sign(data, certificateName_);
                    } catch (net.named_data.jndn.security.SecurityException e) {
                        throw new Error("SecurityException in sign: " + e.getMessage());
                    }
                    try {
                        face.putData(data);
                        Log.i(TAG, "Sent content " + content);
                    } catch (IOException ex) {
                        Log.i(TAG, "Echo: IOException in sending data " + ex.getMessage());
                    }
                }
            } else Log.i(TAG, "Error interest is received.");
//            Message msg = new Message();
//            msg.what = 200; // Result Code ex) Success code: 200 , Fail Code:
//            // 400 ...
//            msg.obj = content; // Result Object
//            m_handler.sendMessage(msg);
        }

        public void
        onRegisterFailed(Name prefix) {
            //++responseCount_;
            Log.i(TAG, "Register failed for prefix " + prefix.toUri());
        }

        KeyChain keyChain_;
        Name certificateName_;
        //int responseCount_ = 0;
    }

    private class ReceiveThread extends Thread {
        public void run() {
            try {
                recvFace = new Face();
                /***
                 * For now, when setting face.setCommandSigningInfo, use a key chain with
                 * a default private key instead of the system default key chain. This
                 * is OK for now because NFD is configured to skip verification, so it
                 * ignores the system default key chain.
                 */
                MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
                MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
                KeyChain keyChain = new KeyChain
                        (new IdentityManager(identityStorage, privateKeyStorage),
                                new SelfVerifyPolicyManager(identityStorage));
                keyChain.setFace(recvFace);

                // Initialize the storage.
                Name keyName = new Name("/testname/DSK-123");
                Name certificateName = keyName.getSubName(0, keyName.size() - 1).append
                        ("KEY").append(keyName.get(-1)).append("ID-CERT").append("0");
                identityStorage.addKey(keyName, KeyType.RSA, new Blob(DEFAULT_RSA_PUBLIC_KEY_DER, false));
                privateKeyStorage.setKeyPairForKeyName
                        (keyName, KeyType.RSA, DEFAULT_RSA_PUBLIC_KEY_DER, DEFAULT_RSA_PRIVATE_KEY_DER);

                recvFace.setCommandSigningInfo(keyChain, certificateName);

                Echo echo = new Echo(keyChain, certificateName);
                Name prefix = new Name(PREFIX);
                Log.i(TAG, "Register prefix  " + prefix.toUri());
                recvFace.registerPrefix(prefix, echo, echo);
                // The main event loop.
                // Wait to receive one interest for the prefix.
                while (true) {
                    recvFace.processEvents();
                    // We need to sleep for a few milliseconds so we don't use 100% of the CPU.
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                Log.e(TAG, "exception: " + e.getMessage());
                sendMsg(401, "Ooops! Fatal errors! Please restart the App!");
            }
        }

        public void recvFaceClose() {
            //recvFace.removePendingInterest(pendingInterestId);
            recvFace.removeRegisteredPrefix(registeredPrefixId);
            recvFace.shutdown();
        }

        private Face recvFace;
        private long registeredPrefixId;
    }

    private static ByteBuffer
    toBuffer(int[] array) {
        ByteBuffer result = ByteBuffer.allocate(array.length);
        for (int i = 0; i < array.length; ++i)
            result.put((byte) (array[i] & 0xff));

        result.flip();
        return result;
    }

    private void sendMsg(int type, String str) {
        Message msg = new Message();
        msg.what = type;
        msg.obj = str;
        m_handler.sendMessage(msg);
    }

    private Handler m_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:  //100: successfully connect
                    m_addrEdit.setEnabled(false);
                    showToast((String) msg.obj);
                    break;
                case 200:  //200: showOwnMessage(msg);
                    m_input.setText("");
                    showMessage((String) msg.obj, ChatLIstViewAdapter.ROLE_OWN);
                    break;
                case 201:  //201: showTargetMsg(msg)
                    showMessage((String) msg.obj, ChatLIstViewAdapter.ROLE_TARGET);
                    break;
                case 300:  //300: someone is trying to connect with you
                    if (m_addrEdit.isEnabled()) {
                        Log.i(TAG, "300!");
                        m_startPassive = true;
                        m_faceAddr = (String) msg.obj;
                        m_addrEdit.setText(m_faceAddr);
                        m_isConnect = true;
                        m_addrEdit.setEnabled(!m_isConnect);
                        m_startSwitch.setChecked(m_isConnect);
                        connect(PASSIVE);
                        showToast((String) msg.obj + "has successfully connect to you!");
                    }
                    break;
                case 401:  //passively discoonect;
                    m_endPassive = true;
                    m_isConnect = false;
                    Log.i(TAG, "401");
                    m_startSwitch.setChecked(m_isConnect);  //close the switch
                    m_addrEdit.setText("");
                    m_addrEdit.setEnabled(!m_isConnect);
                    m_netThread.netFaceClose();
                    showToast((String) msg.obj);
                    break;
                case 400:  //initiatively disconnect
                    Log.i(TAG, "400");
//                    m_startSwitch.setChecked(m_isConnect);  //close the switch
                    m_isConnect = false;
                    m_addrEdit.setEnabled(!m_isConnect);
                    m_addrEdit.setText("");
                    m_netThread.netFaceClose();
                    showToast((String) msg.obj);
                    break;

            }
        }
    };

    private void showToast(String msg) {
        Toast tst = Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG);
        tst.setGravity(Gravity.CENTER | Gravity.TOP, 0, 240);
        tst.show();
    }

    private void showMessage(String msg, int type) {
        Log.i(TAG, "Enter showMessage!");
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put(ChatLIstViewAdapter.KEY_ROLE, type);
        data.put(ChatLIstViewAdapter.KEY_TEXT, msg);

        SimpleDateFormat df1 = new SimpleDateFormat("E MM-dd HH:mm");
        data.put(ChatLIstViewAdapter.KEY_DATE, df1.format(System.currentTimeMillis()).toString());
        data.put(ChatLIstViewAdapter.KEY_SHOW_MSG, true);
        m_chatContent2.add(data);
        m_adapter2.notifyDataSetChanged();
    }
//
//    private void showOwnMessage(String msg){
//        HashMap<String, Object> map = new HashMap<String, Object>();
//        map.put(ChatLIstViewAdapter.KEY_ROLE, ChatLIstViewAdapter.ROLE_OWN);
//        map.put(ChatLIstViewAdapter.KEY_TEXT, msg);
//
//        SimpleDateFormat df2 = new SimpleDateFormat("E MM-dd HH:mm");
//        map.put(ChatLIstViewAdapter.KEY_DATE, df2.format(System.currentTimeMillis()).toString());
//        map.put(ChatLIstViewAdapter.KEY_SHOW_MSG, true);
//        m_chatContent2.add(map);
//        m_adapter2.notifyDataSetChanged();
//    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (m_recvThread != null)
            m_recvThread.recvFaceClose();
    }

    private static final ByteBuffer DEFAULT_RSA_PUBLIC_KEY_DER = toBuffer(new int[]{
            0x30, 0x82, 0x01, 0x22, 0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01,
            0x01, 0x05, 0x00, 0x03, 0x82, 0x01, 0x0f, 0x00, 0x30, 0x82, 0x01, 0x0a, 0x02, 0x82, 0x01, 0x01,
            0x00, 0xb8, 0x09, 0xa7, 0x59, 0x82, 0x84, 0xec, 0x4f, 0x06, 0xfa, 0x1c, 0xb2, 0xe1, 0x38, 0x93,
            0x53, 0xbb, 0x7d, 0xd4, 0xac, 0x88, 0x1a, 0xf8, 0x25, 0x11, 0xe4, 0xfa, 0x1d, 0x61, 0x24, 0x5b,
            0x82, 0xca, 0xcd, 0x72, 0xce, 0xdb, 0x66, 0xb5, 0x8d, 0x54, 0xbd, 0xfb, 0x23, 0xfd, 0xe8, 0x8e,
            0xaf, 0xa7, 0xb3, 0x79, 0xbe, 0x94, 0xb5, 0xb7, 0xba, 0x17, 0xb6, 0x05, 0xae, 0xce, 0x43, 0xbe,
            0x3b, 0xce, 0x6e, 0xea, 0x07, 0xdb, 0xbf, 0x0a, 0x7e, 0xeb, 0xbc, 0xc9, 0x7b, 0x62, 0x3c, 0xf5,
            0xe1, 0xce, 0xe1, 0xd9, 0x8d, 0x9c, 0xfe, 0x1f, 0xc7, 0xf8, 0xfb, 0x59, 0xc0, 0x94, 0x0b, 0x2c,
            0xd9, 0x7d, 0xbc, 0x96, 0xeb, 0xb8, 0x79, 0x22, 0x8a, 0x2e, 0xa0, 0x12, 0x1d, 0x42, 0x07, 0xb6,
            0x5d, 0xdb, 0xe1, 0xf6, 0xb1, 0x5d, 0x7b, 0x1f, 0x54, 0x52, 0x1c, 0xa3, 0x11, 0x9b, 0xf9, 0xeb,
            0xbe, 0xb3, 0x95, 0xca, 0xa5, 0x87, 0x3f, 0x31, 0x18, 0x1a, 0xc9, 0x99, 0x01, 0xec, 0xaa, 0x90,
            0xfd, 0x8a, 0x36, 0x35, 0x5e, 0x12, 0x81, 0xbe, 0x84, 0x88, 0xa1, 0x0d, 0x19, 0x2a, 0x4a, 0x66,
            0xc1, 0x59, 0x3c, 0x41, 0x83, 0x3d, 0x3d, 0xb8, 0xd4, 0xab, 0x34, 0x90, 0x06, 0x3e, 0x1a, 0x61,
            0x74, 0xbe, 0x04, 0xf5, 0x7a, 0x69, 0x1b, 0x9d, 0x56, 0xfc, 0x83, 0xb7, 0x60, 0xc1, 0x5e, 0x9d,
            0x85, 0x34, 0xfd, 0x02, 0x1a, 0xba, 0x2c, 0x09, 0x72, 0xa7, 0x4a, 0x5e, 0x18, 0xbf, 0xc0, 0x58,
            0xa7, 0x49, 0x34, 0x46, 0x61, 0x59, 0x0e, 0xe2, 0x6e, 0x9e, 0xd2, 0xdb, 0xfd, 0x72, 0x2f, 0x3c,
            0x47, 0xcc, 0x5f, 0x99, 0x62, 0xee, 0x0d, 0xf3, 0x1f, 0x30, 0x25, 0x20, 0x92, 0x15, 0x4b, 0x04,
            0xfe, 0x15, 0x19, 0x1d, 0xdc, 0x7e, 0x5c, 0x10, 0x21, 0x52, 0x21, 0x91, 0x54, 0x60, 0x8b, 0x92,
            0x41, 0x02, 0x03, 0x01, 0x00, 0x01
    });

    // Java uses an unencrypted PKCS #8 PrivateKeyInfo, not a PKCS #1 RSAPrivateKey.
    private static final ByteBuffer DEFAULT_RSA_PRIVATE_KEY_DER = toBuffer(new int[]{
            0x30, 0x82, 0x04, 0xbf, 0x02, 0x01, 0x00, 0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7,
            0x0d, 0x01, 0x01, 0x01, 0x05, 0x00, 0x04, 0x82, 0x04, 0xa9, 0x30, 0x82, 0x04, 0xa5, 0x02, 0x01,
            0x00, 0x02, 0x82, 0x01, 0x01, 0x00, 0xb8, 0x09, 0xa7, 0x59, 0x82, 0x84, 0xec, 0x4f, 0x06, 0xfa,
            0x1c, 0xb2, 0xe1, 0x38, 0x93, 0x53, 0xbb, 0x7d, 0xd4, 0xac, 0x88, 0x1a, 0xf8, 0x25, 0x11, 0xe4,
            0xfa, 0x1d, 0x61, 0x24, 0x5b, 0x82, 0xca, 0xcd, 0x72, 0xce, 0xdb, 0x66, 0xb5, 0x8d, 0x54, 0xbd,
            0xfb, 0x23, 0xfd, 0xe8, 0x8e, 0xaf, 0xa7, 0xb3, 0x79, 0xbe, 0x94, 0xb5, 0xb7, 0xba, 0x17, 0xb6,
            0x05, 0xae, 0xce, 0x43, 0xbe, 0x3b, 0xce, 0x6e, 0xea, 0x07, 0xdb, 0xbf, 0x0a, 0x7e, 0xeb, 0xbc,
            0xc9, 0x7b, 0x62, 0x3c, 0xf5, 0xe1, 0xce, 0xe1, 0xd9, 0x8d, 0x9c, 0xfe, 0x1f, 0xc7, 0xf8, 0xfb,
            0x59, 0xc0, 0x94, 0x0b, 0x2c, 0xd9, 0x7d, 0xbc, 0x96, 0xeb, 0xb8, 0x79, 0x22, 0x8a, 0x2e, 0xa0,
            0x12, 0x1d, 0x42, 0x07, 0xb6, 0x5d, 0xdb, 0xe1, 0xf6, 0xb1, 0x5d, 0x7b, 0x1f, 0x54, 0x52, 0x1c,
            0xa3, 0x11, 0x9b, 0xf9, 0xeb, 0xbe, 0xb3, 0x95, 0xca, 0xa5, 0x87, 0x3f, 0x31, 0x18, 0x1a, 0xc9,
            0x99, 0x01, 0xec, 0xaa, 0x90, 0xfd, 0x8a, 0x36, 0x35, 0x5e, 0x12, 0x81, 0xbe, 0x84, 0x88, 0xa1,
            0x0d, 0x19, 0x2a, 0x4a, 0x66, 0xc1, 0x59, 0x3c, 0x41, 0x83, 0x3d, 0x3d, 0xb8, 0xd4, 0xab, 0x34,
            0x90, 0x06, 0x3e, 0x1a, 0x61, 0x74, 0xbe, 0x04, 0xf5, 0x7a, 0x69, 0x1b, 0x9d, 0x56, 0xfc, 0x83,
            0xb7, 0x60, 0xc1, 0x5e, 0x9d, 0x85, 0x34, 0xfd, 0x02, 0x1a, 0xba, 0x2c, 0x09, 0x72, 0xa7, 0x4a,
            0x5e, 0x18, 0xbf, 0xc0, 0x58, 0xa7, 0x49, 0x34, 0x46, 0x61, 0x59, 0x0e, 0xe2, 0x6e, 0x9e, 0xd2,
            0xdb, 0xfd, 0x72, 0x2f, 0x3c, 0x47, 0xcc, 0x5f, 0x99, 0x62, 0xee, 0x0d, 0xf3, 0x1f, 0x30, 0x25,
            0x20, 0x92, 0x15, 0x4b, 0x04, 0xfe, 0x15, 0x19, 0x1d, 0xdc, 0x7e, 0x5c, 0x10, 0x21, 0x52, 0x21,
            0x91, 0x54, 0x60, 0x8b, 0x92, 0x41, 0x02, 0x03, 0x01, 0x00, 0x01, 0x02, 0x82, 0x01, 0x01, 0x00,
            0x8a, 0x05, 0xfb, 0x73, 0x7f, 0x16, 0xaf, 0x9f, 0xa9, 0x4c, 0xe5, 0x3f, 0x26, 0xf8, 0x66, 0x4d,
            0xd2, 0xfc, 0xd1, 0x06, 0xc0, 0x60, 0xf1, 0x9f, 0xe3, 0xa6, 0xc6, 0x0a, 0x48, 0xb3, 0x9a, 0xca,
            0x21, 0xcd, 0x29, 0x80, 0x88, 0x3d, 0xa4, 0x85, 0xa5, 0x7b, 0x82, 0x21, 0x81, 0x28, 0xeb, 0xf2,
            0x43, 0x24, 0xb0, 0x76, 0xc5, 0x52, 0xef, 0xc2, 0xea, 0x4b, 0x82, 0x41, 0x92, 0xc2, 0x6d, 0xa6,
            0xae, 0xf0, 0xb2, 0x26, 0x48, 0xa1, 0x23, 0x7f, 0x02, 0xcf, 0xa8, 0x90, 0x17, 0xa2, 0x3e, 0x8a,
            0x26, 0xbd, 0x6d, 0x8a, 0xee, 0xa6, 0x0c, 0x31, 0xce, 0xc2, 0xbb, 0x92, 0x59, 0xb5, 0x73, 0xe2,
            0x7d, 0x91, 0x75, 0xe2, 0xbd, 0x8c, 0x63, 0xe2, 0x1c, 0x8b, 0xc2, 0x6a, 0x1c, 0xfe, 0x69, 0xc0,
            0x44, 0xcb, 0x58, 0x57, 0xb7, 0x13, 0x42, 0xf0, 0xdb, 0x50, 0x4c, 0xe0, 0x45, 0x09, 0x8f, 0xca,
            0x45, 0x8a, 0x06, 0xfe, 0x98, 0xd1, 0x22, 0xf5, 0x5a, 0x9a, 0xdf, 0x89, 0x17, 0xca, 0x20, 0xcc,
            0x12, 0xa9, 0x09, 0x3d, 0xd5, 0xf7, 0xe3, 0xeb, 0x08, 0x4a, 0xc4, 0x12, 0xc0, 0xb9, 0x47, 0x6c,
            0x79, 0x50, 0x66, 0xa3, 0xf8, 0xaf, 0x2c, 0xfa, 0xb4, 0x6b, 0xec, 0x03, 0xad, 0xcb, 0xda, 0x24,
            0x0c, 0x52, 0x07, 0x87, 0x88, 0xc0, 0x21, 0xf3, 0x02, 0xe8, 0x24, 0x44, 0x0f, 0xcd, 0xa0, 0xad,
            0x2f, 0x1b, 0x79, 0xab, 0x6b, 0x49, 0x4a, 0xe6, 0x3b, 0xd0, 0xad, 0xc3, 0x48, 0xb9, 0xf7, 0xf1,
            0x34, 0x09, 0xeb, 0x7a, 0xc0, 0xd5, 0x0d, 0x39, 0xd8, 0x45, 0xce, 0x36, 0x7a, 0xd8, 0xde, 0x3c,
            0xb0, 0x21, 0x96, 0x97, 0x8a, 0xff, 0x8b, 0x23, 0x60, 0x4f, 0xf0, 0x3d, 0xd7, 0x8f, 0xf3, 0x2c,
            0xcb, 0x1d, 0x48, 0x3f, 0x86, 0xc4, 0xa9, 0x00, 0xf2, 0x23, 0x2d, 0x72, 0x4d, 0x66, 0xa5, 0x01,
            0x02, 0x81, 0x81, 0x00, 0xdc, 0x4f, 0x99, 0x44, 0x0d, 0x7f, 0x59, 0x46, 0x1e, 0x8f, 0xe7, 0x2d,
            0x8d, 0xdd, 0x54, 0xc0, 0xf7, 0xfa, 0x46, 0x0d, 0x9d, 0x35, 0x03, 0xf1, 0x7c, 0x12, 0xf3, 0x5a,
            0x9d, 0x83, 0xcf, 0xdd, 0x37, 0x21, 0x7c, 0xb7, 0xee, 0xc3, 0x39, 0xd2, 0x75, 0x8f, 0xb2, 0x2d,
            0x6f, 0xec, 0xc6, 0x03, 0x55, 0xd7, 0x00, 0x67, 0xd3, 0x9b, 0xa2, 0x68, 0x50, 0x6f, 0x9e, 0x28,
            0xa4, 0x76, 0x39, 0x2b, 0xb2, 0x65, 0xcc, 0x72, 0x82, 0x93, 0xa0, 0xcf, 0x10, 0x05, 0x6a, 0x75,
            0xca, 0x85, 0x35, 0x99, 0xb0, 0xa6, 0xc6, 0xef, 0x4c, 0x4d, 0x99, 0x7d, 0x2c, 0x38, 0x01, 0x21,
            0xb5, 0x31, 0xac, 0x80, 0x54, 0xc4, 0x18, 0x4b, 0xfd, 0xef, 0xb3, 0x30, 0x22, 0x51, 0x5a, 0xea,
            0x7d, 0x9b, 0xb2, 0x9d, 0xcb, 0xba, 0x3f, 0xc0, 0x1a, 0x6b, 0xcd, 0xb0, 0xe6, 0x2f, 0x04, 0x33,
            0xd7, 0x3a, 0x49, 0x71, 0x02, 0x81, 0x81, 0x00, 0xd5, 0xd9, 0xc9, 0x70, 0x1a, 0x13, 0xb3, 0x39,
            0x24, 0x02, 0xee, 0xb0, 0xbb, 0x84, 0x17, 0x12, 0xc6, 0xbd, 0x65, 0x73, 0xe9, 0x34, 0x5d, 0x43,
            0xff, 0xdc, 0xf8, 0x55, 0xaf, 0x2a, 0xb9, 0xe1, 0xfa, 0x71, 0x65, 0x4e, 0x50, 0x0f, 0xa4, 0x3b,
            0xe5, 0x68, 0xf2, 0x49, 0x71, 0xaf, 0x15, 0x88, 0xd7, 0xaf, 0xc4, 0x9d, 0x94, 0x84, 0x6b, 0x5b,
            0x10, 0xd5, 0xc0, 0xaa, 0x0c, 0x13, 0x62, 0x99, 0xc0, 0x8b, 0xfc, 0x90, 0x0f, 0x87, 0x40, 0x4d,
            0x58, 0x88, 0xbd, 0xe2, 0xba, 0x3e, 0x7e, 0x2d, 0xd7, 0x69, 0xa9, 0x3c, 0x09, 0x64, 0x31, 0xb6,
            0xcc, 0x4d, 0x1f, 0x23, 0xb6, 0x9e, 0x65, 0xd6, 0x81, 0xdc, 0x85, 0xcc, 0x1e, 0xf1, 0x0b, 0x84,
            0x38, 0xab, 0x93, 0x5f, 0x9f, 0x92, 0x4e, 0x93, 0x46, 0x95, 0x6b, 0x3e, 0xb6, 0xc3, 0x1b, 0xd7,
            0x69, 0xa1, 0x0a, 0x97, 0x37, 0x78, 0xed, 0xd1, 0x02, 0x81, 0x80, 0x33, 0x18, 0xc3, 0x13, 0x65,
            0x8e, 0x03, 0xc6, 0x9f, 0x90, 0x00, 0xae, 0x30, 0x19, 0x05, 0x6f, 0x3c, 0x14, 0x6f, 0xea, 0xf8,
            0x6b, 0x33, 0x5e, 0xee, 0xc7, 0xf6, 0x69, 0x2d, 0xdf, 0x44, 0x76, 0xaa, 0x32, 0xba, 0x1a, 0x6e,
            0xe6, 0x18, 0xa3, 0x17, 0x61, 0x1c, 0x92, 0x2d, 0x43, 0x5d, 0x29, 0xa8, 0xdf, 0x14, 0xd8, 0xff,
            0xdb, 0x38, 0xef, 0xb8, 0xb8, 0x2a, 0x96, 0x82, 0x8e, 0x68, 0xf4, 0x19, 0x8c, 0x42, 0xbe, 0xcc,
            0x4a, 0x31, 0x21, 0xd5, 0x35, 0x6c, 0x5b, 0xa5, 0x7c, 0xff, 0xd1, 0x85, 0x87, 0x28, 0xdc, 0x97,
            0x75, 0xe8, 0x03, 0x80, 0x1d, 0xfd, 0x25, 0x34, 0x41, 0x31, 0x21, 0x12, 0x87, 0xe8, 0x9a, 0xb7,
            0x6a, 0xc0, 0xc4, 0x89, 0x31, 0x15, 0x45, 0x0d, 0x9c, 0xee, 0xf0, 0x6a, 0x2f, 0xe8, 0x59, 0x45,
            0xc7, 0x7b, 0x0d, 0x6c, 0x55, 0xbb, 0x43, 0xca, 0xc7, 0x5a, 0x01, 0x02, 0x81, 0x81, 0x00, 0xab,
            0xf4, 0xd5, 0xcf, 0x78, 0x88, 0x82, 0xc2, 0xdd, 0xbc, 0x25, 0xe6, 0xa2, 0xc1, 0xd2, 0x33, 0xdc,
            0xef, 0x0a, 0x97, 0x2b, 0xdc, 0x59, 0x6a, 0x86, 0x61, 0x4e, 0xa6, 0xc7, 0x95, 0x99, 0xa6, 0xa6,
            0x55, 0x6c, 0x5a, 0x8e, 0x72, 0x25, 0x63, 0xac, 0x52, 0xb9, 0x10, 0x69, 0x83, 0x99, 0xd3, 0x51,
            0x6c, 0x1a, 0xb3, 0x83, 0x6a, 0xff, 0x50, 0x58, 0xb7, 0x28, 0x97, 0x13, 0xe2, 0xba, 0x94, 0x5b,
            0x89, 0xb4, 0xea, 0xba, 0x31, 0xcd, 0x78, 0xe4, 0x4a, 0x00, 0x36, 0x42, 0x00, 0x62, 0x41, 0xc6,
            0x47, 0x46, 0x37, 0xea, 0x6d, 0x50, 0xb4, 0x66, 0x8f, 0x55, 0x0c, 0xc8, 0x99, 0x91, 0xd5, 0xec,
            0xd2, 0x40, 0x1c, 0x24, 0x7d, 0x3a, 0xff, 0x74, 0xfa, 0x32, 0x24, 0xe0, 0x11, 0x2b, 0x71, 0xad,
            0x7e, 0x14, 0xa0, 0x77, 0x21, 0x68, 0x4f, 0xcc, 0xb6, 0x1b, 0xe8, 0x00, 0x49, 0x13, 0x21, 0x02,
            0x81, 0x81, 0x00, 0xb6, 0x18, 0x73, 0x59, 0x2c, 0x4f, 0x92, 0xac, 0xa2, 0x2e, 0x5f, 0xb6, 0xbe,
            0x78, 0x5d, 0x47, 0x71, 0x04, 0x92, 0xf0, 0xd7, 0xe8, 0xc5, 0x7a, 0x84, 0x6b, 0xb8, 0xb4, 0x30,
            0x1f, 0xd8, 0x0d, 0x58, 0xd0, 0x64, 0x80, 0xa7, 0x21, 0x1a, 0x48, 0x00, 0x37, 0xd6, 0x19, 0x71,
            0xbb, 0x91, 0x20, 0x9d, 0xe2, 0xc3, 0xec, 0xdb, 0x36, 0x1c, 0xca, 0x48, 0x7d, 0x03, 0x32, 0x74,
            0x1e, 0x65, 0x73, 0x02, 0x90, 0x73, 0xd8, 0x3f, 0xb5, 0x52, 0x35, 0x79, 0x1c, 0xee, 0x93, 0xa3,
            0x32, 0x8b, 0xed, 0x89, 0x98, 0xf1, 0x0c, 0xd8, 0x12, 0xf2, 0x89, 0x7f, 0x32, 0x23, 0xec, 0x67,
            0x66, 0x52, 0x83, 0x89, 0x99, 0x5e, 0x42, 0x2b, 0x42, 0x4b, 0x84, 0x50, 0x1b, 0x3e, 0x47, 0x6d,
            0x74, 0xfb, 0xd1, 0xa6, 0x10, 0x20, 0x6c, 0x6e, 0xbe, 0x44, 0x3f, 0xb9, 0xfe, 0xbc, 0x8d, 0xda,
            0xcb, 0xea, 0x8f
    });


}
