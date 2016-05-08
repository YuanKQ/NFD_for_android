package net.named_data.nfd;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;

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
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.NoVerifyPolicyManager;
import net.named_data.jndn.sync.ChronoSync2013;
import net.named_data.jndn.util.Blob;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Created by yuan on 16-4-21.
 */
public class ChronoChatFragment extends Fragment {

//    private EditText m_edChronoFace;
    private EditText m_edHubPrefix;
    private EditText m_edRoomName;
    private EditText m_edUsrName;
    private Switch m_switchStart;
    private static TextView m_txMsg;
    private EditText m_txInput;
    private Button m_btnSend;

    private String m_usrName = "";
    private String m_hubPrefix = "";
//    private String m_chronoFace = "";
    private String m_roomName = "";
    private TestChronoChat m_chronoChater;

    private final static int MAXLEN = 1000;
    private final static String TAG = "NDN";
    final static int session = (int)Math.round(getNowMilliseconds() / 1000.0);
    private NetTool m_netTool;
    private ArrayList<HashMap<String, Face>> m_faceList = new ArrayList<HashMap<String, Face>>();

    public static Fragment newInstance() {
        return new ChronoChatFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate!");
//        m_ChronoChater = new TestChronoChat();
//        m_ChronoChater.start();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        View v = inflater.inflate(R.layout.activity_chronochat, null);
//        m_edChronoFace = (EditText) v.findViewById(R.id.ed_chronoFace);
        m_edHubPrefix = (EditText) v.findViewById(R.id.ed_hubPrefix);
        m_edRoomName = (EditText) v.findViewById(R.id.ed_roomName);
        m_edUsrName = (EditText) v.findViewById(R.id.ed_usrName);
        m_txMsg = (TextView) v.findViewById(R.id.tv_msg);
        m_switchStart = (Switch) v.findViewById(R.id.switch_chronStart);
        m_switchStart.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                Log.i(TAG, "isChecked=" + isChecked);
                if (isChecked) {
                    if (m_edUsrName.getText().toString().equals("")) {
                        m_switchStart.setChecked(false);
                        showToast("Please input the user name!");
                        return;
                    } else {
                        m_usrName = m_edUsrName.getText().toString();
                    }

                    if (m_edHubPrefix.getText().toString().equals("")) {
                        m_hubPrefix = "/" + m_edHubPrefix.getHint().toString(); //Must start with "/"
                    } else {
                        m_hubPrefix = "/" + m_edHubPrefix.getText().toString();
                    }

                    if (m_edRoomName.getText().toString().equals("")) {
                        m_roomName = "/" + m_edRoomName.getHint().toString(); //Must start with "/"
                    } else {
                        m_roomName = "/" + m_edRoomName.getText().toString();
                    }

                    m_edHubPrefix.setText(m_hubPrefix);
                    m_edHubPrefix.setEnabled(false);
                    m_edRoomName.setText(m_roomName);
                    m_edRoomName.setEnabled(false);
                    m_edUsrName.setEnabled(false);
                    m_txMsg.setText("");

                    //Find all devices installed in the same LAN
                    m_netTool = new NetTool(getContext(), m_hubPrefix, m_roomName, session);
                    m_netTool.scan();


                    m_chronoChater = new TestChronoChat(m_usrName, m_hubPrefix, m_roomName, getContext());
                    m_chronoChater.toStart();
                    m_chronoChater.start();

//                    final ProgressDialog proDlg = ProgressDialog.show(getContext(), "", "waiting...");
//                    m_handler.postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            Log.i(TAG, "progressDlg End!");
//                            proDlg.dismiss();
//
//                        }
//                    }, 30000);

                } else {
//                    m_chronoFace = "";
                    m_edHubPrefix.setEnabled(true);
                    m_edRoomName.setEnabled(true);
                    m_edUsrName.setEnabled(true);
                    if (m_chronoChater != null)
                        m_chronoChater.end();
                }
            }
        });

        m_txInput = (EditText) v.findViewById(R.id.ed_input);
        m_btnSend = (Button) v.findViewById(R.id.btn_send);
        m_btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String tmp = m_txInput.getText().toString();
                if (tmp.equals("")) {
                    showToast("The message can't be empty!");
                    return;
                }
                if (tmp.length() > MAXLEN) {
                    showToast("The length of message is too long!");
                    return;
                }

                if (m_switchStart.isChecked() == false) {
                    showToast("Please connect first!");
                    return;
                }
                m_txInput.setText("");
                m_chronoChater.setReady(tmp);
            }
        });

        return v;
    }

    /////////////////////////////////////////////////////////////////////////////////
    /***
    * ChronoChat Operation
    */
    class Chat implements ChronoSync2013.OnInitialized,
            ChronoSync2013.OnReceivedSyncState, OnData, OnInterestCallback {

        private long prefixID_;
        private String IP_;

        public Chat
                (String screenName, String chatRoom, Name hubPrefix, Face face, Context ctx,
                 KeyChain keyChain, Name certificateName)
        {
            Log.i(TAG, "Chat Create!");
            screenName_ = screenName;
            chatRoom_ = chatRoom;
            face_ = face;
            keyChain_ = keyChain;
            certificateName_ = certificateName;
            heartbeat_ = this.new Heartbeat();

            // This should only be called once, so get the random string here.
            chatPrefix_ = new Name(hubPrefix + chatRoom_).append(getRandomString());
//            int session = (int)Math.round(getNowMilliseconds() / 1000.0);
            userName_ = screenName_ + session;


            //initialize the m_faceList
            IP_ = m_netTool.getLocAddr();
            HashMap<String, Face>map = new HashMap<String, Face>();
            map.put(IP_, new Face(IP_));
            m_faceList.add(map);

            try {
                sync_ = new ChronoSync2013
                        (this, this, chatPrefix_,
                                new Name("/ndn/broadcast/ChronoChat-0.3" + chatRoom_), session,
                                face, keyChain, certificateName, syncLifetime_, RegisterFailed.onRegisterFailed_);
            } catch (IOException | SecurityException ex) {
                Log.e(TAG, "IOException | SecurityException ex in Chat constructor: sync_");
//                Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }

            try {
                prefixID_ = face.registerPrefix(chatPrefix_, this, RegisterFailed.onRegisterFailed_);
                sendMsg(100, "@@@Register:" + chatPrefix_.toString());
            } catch (IOException | SecurityException ex) {
                Log.e(TAG, "IOException | SecurityException ex in Chat constructor: registerPrefix");
//                Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        public void chatEnd() {
            face_.removeRegisteredPrefix(prefixID_);
            face_.shutdown();
        }

        // Send a chat message.
        public final void
        sendMessage(String chatMessage) throws IOException, SecurityException
        {
//            //sendDebugMsg("-------------------------");
//            //sendDebugMsg("sendMessage:");
            if (messageCache_.size() == 0)
                messageCacheAppend(ChatbufProto.ChatMessage.ChatMessageType.JOIN, "xxx");

            // Ignore an empty message.
            // forming Sync Data Packet.
            if (!chatMessage.equals("")) {
                sync_.publishNextSequenceNo();
//                //sendDebugMsg("	#sequenceNo:" + sync_.getSequenceNo());
                messageCacheAppend(ChatbufProto.ChatMessage.ChatMessageType.CHAT, chatMessage);
                sendMsg(200, screenName_ + ": " + chatMessage);
                Log.i(TAG, screenName_ + ": " + chatMessage);
            }
        }

        // Send leave message and leave.
        public final void
        leave() throws IOException, SecurityException
        {
            //sendDebugMsg("-------------------------");
            //sendDebugMsg("leave()");
            sync_.publishNextSequenceNo();
            //sendDebugMsg("	#sequenceNo:" + sync_.getSequenceNo());
            messageCacheAppend(ChatbufProto.ChatMessage.ChatMessageType.LEAVE, "xxx");

            Log.i(TAG, "Chat leave!");
        }



        // initial: push the JOIN message in to the messageCache_, update roster and
        // start the heartbeat.
        // (Do not call this. It is only public to implement the interface.)
        public final void
        onInitialized()
        {
//            //sendDebugMsg("-------------------------");
            ////sendDebugMsg("onInitialized:");
            // Set the heartbeat timeout using the Interest timeout mechanism. The
            // heartbeat() function will call itself again after a timeout.
            // TODO: Are we sure using a "/local/timeout" interest is the best future call approach?
            Interest timeout = new Interest(new Name("/local/timeout"));
            timeout.setInterestLifetimeMilliseconds(60000);
            try {
                face_.expressInterest(timeout, DummyOnData.onData_, heartbeat_);
            } catch (IOException ex) {
                Log.e(TAG, "IOException in onInialized!!");
//                Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }

            if (roster_.indexOf(userName_) < 0) {
                roster_.add(userName_);
                sendMsg(200, "Member: " + screenName_);
                sendMsg(200, screenName_ + ": Join");
                messageCacheAppend(ChatbufProto.ChatMessage.ChatMessageType.JOIN, "xxx");

                HashMap<String, String> map = new HashMap<String, String>();
                map.put(userName_, String.valueOf(0));
                sequence_.add(map);
            }
        }

        // sendInterest: Send a Chat Interest to fetch chat messages after the
        // user gets the Sync data packet back but will not send interest.
        // (Do not call this. It is only public to implement the interface.)
        public final void
        onReceivedSyncState(List syncStates, boolean isRecovery)
        {
            //sendDebugMsg("-------------------------");
            //sendDebugMsg("onReceivedSyncState:");
            Face sendFace;
            sendFace = m_faceList.get(0).get(IP_);
            // This is used by onData to decide whether to display the chat messages.
            isRecoverySyncState_ = isRecovery;
//            //sendDebugMsg("	isRecovery: " + isRecovery);
            ArrayList<Face> faceList  = new ArrayList<Face>();
            ArrayList sendList = new ArrayList(); // of String
            ArrayList sessionNoList = new ArrayList(); // of long
            ArrayList sequenceNoList = new ArrayList(); // of long
            for (int j = 0; j < syncStates.size(); ++j) {
                ChronoSync2013.SyncState syncState = (ChronoSync2013.SyncState)syncStates.get(j);
                Name nameComponents = new Name(syncState.getDataPrefix());
                String tempName = nameComponents.get(-1).toEscapedString();
                long sessionNo = syncState.getSessionNo();
                String tmpIP = m_netTool.findFaceUri(String.valueOf(sessionNo));
//                //sendDebugMsg("  sendFace Uri:" + tmpIP);
                boolean isExist = false;
                if (tmpIP != null) {
                    for (int i = 0; i < m_faceList.size(); i ++) {
                        if (m_faceList.get(i).containsKey(tmpIP)) {
//                            //sendDebugMsg("  m_faceList has contained: " + m_faceList.get(i));
                            sendFace = m_faceList.get(i).get(tmpIP);
                            isExist = true;
                            break;
                        }
                    }
//                    if (i >= m_faceList.size()) {
//                        HashMap<String, Face> map = new HashMap<String, Face>();
//                        sendFace = new Face(tmpIP);
//                        map.put(tmpIP, sendFace);
//                        //sendDebugMsg("  m_faceList add new item:" + map);
//                        m_faceList.add(map);
//                    }
                } else {
                    //sendDebugMsg("  Can't not find the faceUri corresponding to the sessionNO!");
                }
                if (!isExist) {
                    HashMap<String, Face> map = new HashMap<String, Face>();
                    sendFace = new Face(tmpIP);
                    map.put(tmpIP, sendFace);
//                    //sendDebugMsg("  m_faceList add new item:" + map);
                    m_faceList.add(map);

//                    m_netTool.addFaceItem();
                }
                faceList.add(sendFace);
                //sendDebugMsg("	nameComponent:" + nameComponents.toString() + " tempName:" + tempName + " sessionNo:" + sessionNo);
                if (!tempName.equals(screenName_)) {
                    int index = -1;
                    for (int k = 0; k < sendList.size(); ++k) {
                        if (((String)sendList.get(k)).equals(syncState.getDataPrefix())) {
//                            //sendDebugMsg("	(String)sendList.get(k):" + (String)sendList.get(k));
                            index = k;
                            break;
                        }
                    }
                    if (index != -1) {
                        sessionNoList.set(index, sessionNo);
                        sequenceNoList.set(index, syncState.getSequenceNo());
                    }
                    else{
                        sendList.add(syncState.getDataPrefix());
                        sessionNoList.add(sessionNo);
                        sequenceNoList.add(syncState.getSequenceNo());
                    }
                }
            }

            for (int i = 0; i < sendList.size(); ++i) {
                String uri = (String)sendList.get(i) + "/" + (long)sessionNoList.get(i) +
                        "/" + (long)sequenceNoList.get(i);
                Interest interest = new Interest(new Name(uri));
                interest.setInterestLifetimeMilliseconds(syncLifetime_);
                try {
//                    face_.expressInterest(interest, this, ChatTimeout.onTimeout_);
//                    m_faceList.get(0).get(IP_).expressInterest(interest, this, ChatTimeout.onTimeout_);
                    faceList.get(i).expressInterest(interest, this, ChatTimeout.onTimeout_);
//                    Interest interest1 = new Interest(new Name("/yuan/test"));
//                    faceList.get(i).expressInterest(interest1, this, ChatTimeout.onTimeout_);
//                    //sendDebugMsg("Finally sendFace is " + faceList.get(i));
//                    //sendDebugMsg("<<onReceivedSyncState interest:" + uri);
                    faceList.get(i).processEvents();
                    Thread.sleep(10);
                } catch (Exception ex) {
                    Log.e(TAG, "IOException in onReceivedSyncState!");
//                    Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
            }
        }

        // Send back a Chat Data Packet which contains the user's message.
        // (Do not call this. It is only public to implement the interface.)
        public final void
        onInterest
        (Name prefix, Interest interest, Face face, long interestFilterId,
         InterestFilter filter)
        {
            //sendDebugMsg("-------------------------");
            //sendDebugMsg("onInterest: ");
//            //sendDebugMsg("	>>prefix:" + prefix.toString());
            //sendDebugMsg("	>>interest:" + interest.getName().toString());
            ChatbufProto.ChatMessage.Builder builder = ChatbufProto.ChatMessage.newBuilder();
            long sequenceNo = Long.parseLong(interest.getName().get(chatPrefix_.size() + 1).toEscapedString());
            //sendDebugMsg("	sequenceNo: " + sequenceNo);
            boolean gotContent = false;
            for (int i = messageCache_.size() - 1; i >= 0; --i) {
                CachedMessage message = (CachedMessage)messageCache_.get(i);
                //sendDebugMsg("	msgType:" + message.getMessageType() + "	msg:" + message.getMessage());
                if (message.getSequenceNo() == sequenceNo) {

                    if (!message.getMessageType().equals(ChatbufProto.ChatMessage.ChatMessageType.CHAT)) {
                        builder.setFrom(screenName_);
                        builder.setTo(chatRoom_);
                        builder.setType(message.getMessageType());
                        builder.setTimestamp((int)Math.round(message.getTime() / 1000.0));
                    }
                    else {
                        builder.setFrom(screenName_);
                        builder.setTo(chatRoom_);
                        builder.setType(message.getMessageType());
                        builder.setData(message.getMessage());
                        builder.setTimestamp((int)Math.round(message.getTime() / 1000.0));
                    }
                    gotContent = true;
                    break;
                }
            }

            if (gotContent) {
                ChatbufProto.ChatMessage content = builder.build();
                byte[] array = content.toByteArray();
                Data data = new Data(interest.getName());
                data.setContent(new Blob(array, false));
                try {
                    keyChain_.sign(data, certificateName_);
                } catch (SecurityException ex) {
                    Log.e(TAG, "SecurityException ex in onInterest!");
//                    Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
                try {
                    face.putData(data);
                    //sendDebugMsg("	<<onInterest:sendData [screeName]" + builder.getFrom() + " [chatRoom]" + builder.getTo() + " [msgType]" + builder.getType() + " [data]" + builder.getData());
                } catch (IOException ex) {
                    Log.e(TAG, "IOException in onInterest!");
//                    Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        // Process the incoming Chat data.
        // (Do not call this. It is only public to implement the interface.)
        public final void
        onData(Interest interest, Data data)
        {
            //sendDebugMsg("-------------------------");
            //sendDebugMsg("onData:");
            //sendDebugMsg("	>>interest:" + interest.getName().toString());

            Log.i(TAG, ">>onData:" + data.getContent().toString());

            ChatbufProto.ChatMessage content;
            try {
                content = ChatbufProto.ChatMessage.parseFrom(data.getContent().getImmutableArray());
            } catch (InvalidProtocolBufferException ex) {
                Log.e(TAG, "InvalidProtocolBufferException in onData!");
//                Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            if (getNowMilliseconds() - content.getTimestamp() * 1000.0 < 180000.0) {
                String name = content.getFrom();
                String prefix = data.getName().getPrefix(-2).toUri();
                long sessionNo = Long.parseLong(data.getName().get(-2).toEscapedString());
                long sequenceNo = Long.parseLong(data.getName().get(-1).toEscapedString());
                String nameAndSession = name + sessionNo;
                //sendDebugMsg("	data name:" + name + " prefix:" + prefix + " session:" + sessionNo + " sequence:" + sequenceNo);

                int l = 0;
                //update roster
                while (l < roster_.size()) {
                    String entry = (String) roster_.get(l);
                    String tempName = entry.substring(0, entry.length() - 10);
                    long tempSessionNo = Long.parseLong(entry.substring(entry.length() - 10));
                    //sendDebugMsg("roster[" + l + "] tempname:" + tempName + " tempsession:" + tempSessionNo);
                    if (!name.equals(tempName) && !content.getType().equals(ChatbufProto.ChatMessage.ChatMessageType.LEAVE))
                        ++l;
                    else {
                        if (name.equals(tempName) && sessionNo > tempSessionNo)
                            roster_.set(l, nameAndSession);
                        break;
                    }
                }

                if (l == roster_.size()) {
                    roster_.add(nameAndSession);
                    sendMsg(200, name + ": Join");
                }

                //update the sequence_
                boolean isPrint = true;
                int i = 0;
                for (; i < sequence_.size(); ++i) {
                    HashMap<String, String> map = sequence_.get(i);
                    if (map.containsKey(nameAndSession)) {
//                        //sendDebugMsg("  " + map.toString() + " contains key: " + nameAndSession);
                        String mapSequence = map.get(nameAndSession);
                        if (mapSequence.compareTo(String.valueOf(sequenceNo)) >= 0) {
                            isPrint = false;
                            break;
                        }

                        HashMap<String, String> tmpMap = new HashMap<String, String>();
                        tmpMap.put(nameAndSession, String.valueOf(sequenceNo));
                        sequence_.set(i, tmpMap);
//                        //sendDebugMsg("tmpMap: " + tmpMap.toString());
                    }
                }
                if (i >= sequence_.size()) {
                    HashMap<String, String> tmpMap = new HashMap<String, String>();
                    tmpMap.put(nameAndSession, String.valueOf(sequenceNo));
                    sequence_.add(tmpMap);
//                    //sendDebugMsg("sequence_ add new item: " + tmpMap);
                }


                // Set the alive timeout using the Interest timeout mechanism.
                // TODO: Are we sure using a "/local/timeout" interest is the best future call approach?
                Interest timeout = new Interest(new Name("/local/timeout"));
                timeout.setInterestLifetimeMilliseconds(120000);
                try {
                    face_.expressInterest
                            (timeout, DummyOnData.onData_,
                                    this.new Alive(sequenceNo, name, sessionNo, prefix));
                } catch (IOException ex) {
                    Log.e(TAG, "IOException in onData!");
//                    Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }

                //sendDebugMsg("  isPrint: " + isPrint);
//                //sendDebugMsg("isRecoverySyncState_:" + isRecoverySyncState_);
                // isRecoverySyncState_ was set by sendInterest.
                // TODO: If isRecoverySyncState_ changed, this assumes that we won't get
                //   data from an interest sent before it changed.
                //sendDebugMsg("  content: [type]" + content.getType() + " [userName]" + content.getFrom() + " [data]" + content.getData());
                if (isPrint && content.getType().equals(ChatbufProto.ChatMessage.ChatMessageType.CHAT) &&
                        sessionNo != session)  //!isRecoverySyncState_ && content.getFrom().equals(screenName_)
                        {
                            sendMsg(200, content.getFrom() + ": " + content.getData());
                        }
                else if (content.getType().equals(ChatbufProto.ChatMessage.ChatMessageType.LEAVE)) {
                    // leave message
                    int n = roster_.indexOf(nameAndSession);
                    if (n >= 0 && !name.equals(screenName_)) {
                        roster_.remove(n);
                        sendMsg(200, name + ": Leave");
                    }

                    for (int j = 0; j < sequence_.size(); ++ j) {
                        HashMap<String, String> map = sequence_.get(j);
                        if (map.containsKey(nameAndSession) && !nameAndSession.contains(screenName_)) {
                            sequence_.remove(i);
                            //sendDebugMsg("remove sequence_ item: " + map.toString());
                            break;
                        }
                    }
                }
            }
        }

        /**
         * This repeatedly calls itself after a timeout to send a heartbeat message
         * (chat message type HELLO).
         * This method has an "interest" argument because we use it as the onTimeout
         * for Face.expressInterest.
         */
        private class Heartbeat implements OnTimeout {
            public final void
            onTimeout(Interest interest) {
//                //sendDebugMsg("------------------------");
//                //sendDebugMsg("Heartbeat: onTimeout");
//                //sendDebugMsg("	>>interest:" + interest.getName().toString());

                if (messageCache_.size() == 0)
                    messageCacheAppend(ChatbufProto.ChatMessage.ChatMessageType.JOIN, "xxx");

                try {
                    sync_.publishNextSequenceNo();
                } catch (IOException | SecurityException ex) {
//                    Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                    Log.e(TAG, "IOException | SecurityException ex in Heartbeat.");
                    return;
                }
                messageCacheAppend(ChatbufProto.ChatMessage.ChatMessageType.HELLO, "xxx");

                // Call again.
                // TODO: Are we sure using a "/local/timeout" interest is the best future call approach?
                Interest timeout = new Interest(new Name("/local/timeout"));
                timeout.setInterestLifetimeMilliseconds(60000);
                try {
                    face_.expressInterest(timeout, DummyOnData.onData_, heartbeat_);
                } catch (IOException ex) {
//                    Logger.getLogger(Chat.class.getName()).log(Level.SEVERE, null, ex);
                    Log.i(TAG, "IOException in Heartbeat.");
                }
            }
        }

        /**
         * This is called after a timeout to check if the user with prefix has a newer
         * sequence number than the given temp_seq. If not, assume the user is idle and
         * remove from the roster and print a leave message.
         * This is used as the onTimeout for Face.expressInterest.
         */
        private class Alive implements OnTimeout {
            public Alive(long tempSequenceNo, String name, long sessionNo, String prefix)
            {
                tempSequenceNo_ = tempSequenceNo;
                name_ = name;
                sessionNo_ = sessionNo;
                prefix_ = prefix;

//                //sendDebugMsg("---------------------------");
               // //sendDebugMsg("Alive constructor:");
//                //sendDebugMsg("	tempSequenceNo:" + tempSequenceNo + " name:" + name + " sessionNo" + sessionNo + " prefix:" + prefix);

            }

            public final void
            onTimeout(Interest interest)
            {
                //sendDebugMsg("-------------------------");
                //sendDebugMsg("Alive: onTimeout");
                //sendDebugMsg("	>>interest:" + interest.getName().toString());
                long sequenceNo = sync_.getProducerSequenceNo(prefix_, sessionNo_);
                String nameAndSession = name_ + sessionNo_;
                //sendDebugMsg("	nameAndSession: " + nameAndSession);
                int n = roster_.indexOf(nameAndSession);
                if (sequenceNo != -1 && n >= 0) {
                    if (tempSequenceNo_ == sequenceNo) {
                        roster_.remove(n);
                        sendMsg(200, name_ + ": Leave");
                    }
                }

                for (int i = 0; i < sequence_.size(); ++ i) {
                    HashMap<String, String> map = sequence_.get(i);
                    if (map.containsKey(nameAndSession) && map.get(nameAndSession).equals(sequenceNo)) {
                        sequence_.remove(i);
                        //sendDebugMsg("  remove the sequence_ item: " + map.toString());
                        break;
                    }
                }
            }

            private final long tempSequenceNo_;
            private final String name_;
            private final long sessionNo_;
            private final String prefix_;
        }

        /**
         * Append a new CachedMessage to messageCache_, using given messageType and message,
         * the sequence number from sync_.getSequenceNo() and the current time. Also
         * remove elements from the front of the cache as needed to keep
         * the size to maxMessageCacheLength_.
         */
        private void
        messageCacheAppend(ChatbufProto.ChatMessage.ChatMessageType messageType, String message)
        {
            messageCache_.add(new CachedMessage
                    (sync_.getSequenceNo(), messageType, message, getNowMilliseconds()));
            while (messageCache_.size() > maxMessageCacheLength_)
                messageCache_.remove(0);
        }



        // Use a non-template ArrayList so it works with older Java compilers.
        private final ArrayList messageCache_ = new ArrayList(); // of CachedMessage
        private final ArrayList roster_ = new ArrayList(); // of String
        private final ArrayList<HashMap<String, String>> sequence_ = new ArrayList<HashMap<String, String>>();
        private final int maxMessageCacheLength_ = 100;
        private boolean isRecoverySyncState_ = true;
        private final String screenName_;
        private final String chatRoom_;
        private final String userName_;
        private final Name chatPrefix_;
        private final double syncLifetime_ = 5000.0; // milliseconds
        private ChronoSync2013 sync_;
        private final Face face_;
        private final KeyChain keyChain_;
        private final Name certificateName_;
        private final OnTimeout heartbeat_;
    }

    private void sendMsg(int i, String s) {
        Message msg = new Message();
        msg.what = i;
        msg.obj = s + "\n";
        m_handler.sendMessage(msg);
    }

    // Generate a random name for ChronoSync.
    private static String
    getRandomString()
    {
        String seed = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM0123456789";
        String result = "";
        Random random = new Random();
        for (int i = 0; i < 10; ++i) {
            // Using % means the distribution isn't uniform, but that's OK.
            int position = random.nextInt(256) % seed.length();
            result += seed.charAt(position);
        }

        return result;
    }

    private static ByteBuffer
    toBuffer(int[] array)
    {
        ByteBuffer result = ByteBuffer.allocate(array.length);
        for (int i = 0; i < array.length; ++i)
            result.put((byte)(array[i] & 0xff));

        result.flip();
        return result;
    }

    private static final ByteBuffer DEFAULT_RSA_PUBLIC_KEY_DER = toBuffer(new int[] {
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
    private static final ByteBuffer DEFAULT_RSA_PRIVATE_KEY_DER = toBuffer(new int[] {
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

    private static class RegisterFailed implements OnRegisterFailed {
        public final void
        onRegisterFailed(Name prefix)
        {
            //sendDebugMsg("Register failed for prefix " + prefix.toUri());
            Log.e(TAG, "Register failed for prefix " + prefix.toUri());
        }

        public final static OnRegisterFailed onRegisterFailed_ = new RegisterFailed();
    }

    // This is a do-nothing onData for using expressInterest for timeouts.
    // This should never be called.
    private static class DummyOnData implements OnData {
        public final void
        onData(Interest interest, Data data) {
            //sendDebugMsg("-----------------------");
            //sendDebugMsg("DummyOnData");
        }

        public final static OnData onData_ = new DummyOnData();
    }

    private static class CachedMessage {
        public CachedMessage
                (long sequenceNo, ChatbufProto.ChatMessage.ChatMessageType messageType, String message, double time)
        {
            sequenceNo_ = sequenceNo;
            messageType_ = messageType;
            message_ = message;
            time_ = time;
        }

        public final long
        getSequenceNo() { return sequenceNo_; }

        public final ChatbufProto.ChatMessage.ChatMessageType
        getMessageType() { return messageType_; }

        public final String
        getMessage() { return message_; }

        public final double
        getTime() { return time_; }

        private final long sequenceNo_;
        private final ChatbufProto.ChatMessage.ChatMessageType messageType_;
        private final String message_;
        private final double time_;
    }

    private static class ChatTimeout implements OnTimeout {
        public final void
        onTimeout(Interest interest) {
            //sendDebugMsg("-------------------");
            //sendDebugMsg("ChatTimeout: ");
            //sendDebugMsg("	>>interest:" + interest.getName().toString());
            //sendDebugMsg("Timeout waiting for chat data");
            Log.e(TAG, "Timeout waiting for chat data");
        }

        public final static OnTimeout onTimeout_ = new ChatTimeout();
    }

    /**
     * Get the current time in milliseconds.
     * @return  The current time in milliseconds since 1/1/1970, including
     * fractions of a millisecond.
     */
    public static double
    getNowMilliseconds() { return (double)System.currentTimeMillis(); }


    class TestChronoChat extends Thread {
        // Convert the int array to a ByteBuffer.


//    private static class RegisterFailed implements OnRegisterFailed {
//        public final void
//        onRegisterFailed(Name prefix)
//        {
//            //sendDebugMsg("Register failed for prefix " + prefix.toUri());
//        }
//
//        public final static OnRegisterFailed onRegisterFailed_ = new RegisterFailed();
//    }

        private String screenName;
        private String hubPrefix;
        private String chatRoom;
        private String host;
        private Face chronoface;
        private boolean isConnect = false;
        private boolean isReady = false;
        private String input = "";
        private Context m_ctx;

        public TestChronoChat(String screenName, String hubPrefix, String chatRoom, Context ctx) {
            this.screenName = screenName;
            this.hubPrefix = hubPrefix;
            this.chatRoom = chatRoom;
            m_ctx = ctx;
            Log.i(TAG, "TestChronoChat is started!");
        }

        public void toStart() {
            Log.i(TAG, "TestChronoChat start!");
            isConnect = true;
        }

        public void end() {
            isConnect = false;
        }

        public void setReady(String str) {
            Log.i(TAG, "TestChronoChat is ready to send: " + str);
            input = str;
            isReady = true;
        }

        @Override
        public void run()
        {
            try {
                Log.i(TAG, "TestChronoChat Run!");
//            //sendDebugMsg("Enter your chat username:");
//            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
//            screenName = reader.readLine();
//
//            String defaultHubPrefix = "ndn/edu/ucla/remap";
//            //sendDebugMsg("Enter your hub prefix [" + defaultHubPrefix + "]:");
//            hubPrefix = reader.readLine();
//            if (hubPrefix.equals(""))
//                hubPrefix = defaultHubPrefix;
//
//            String defaultChatRoom = "ndnchat";
//            //sendDebugMsg("Enter the chatroom name [" + defaultChatRoom + "]:");
//            chatRoom = reader.readLine();
//            if (chatRoom.equals(""))
//                chatRoom = defaultChatRoom;
//
//            host = "localhost";
//            //sendDebugMsg("Connecting to " + host + ", Chatroom: " + chatRoom +
//                    ", Username: " + screenName);
//            //sendDebugMsg("");

                // Set up the key chain.
                chronoface = new Face();

                MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
                MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
                KeyChain keyChain = new KeyChain
                        (new IdentityManager(identityStorage, privateKeyStorage),
                                new NoVerifyPolicyManager());
                keyChain.setFace(chronoface);
                Name keyName = new Name("/testname/DSK-123");
                Name certificateName = keyName.getSubName(0, keyName.size() - 1).append
                        ("KEY").append(keyName.get(-1)).append("ID-CERT").append("0");
                identityStorage.addKey(keyName, KeyType.RSA, new Blob(DEFAULT_RSA_PUBLIC_KEY_DER, false));
                privateKeyStorage.setKeyPairForKeyName
                        (keyName, KeyType.RSA, DEFAULT_RSA_PUBLIC_KEY_DER, DEFAULT_RSA_PRIVATE_KEY_DER);
                chronoface.setCommandSigningInfo(keyChain, certificateName);

                Chat chat = new Chat
                        (screenName, chatRoom, new Name(hubPrefix), chronoface, m_ctx, keyChain, certificateName);

                // The main loop to process Chat while checking stdin to send a message.
//            //sendDebugMsg("Enter your chat message. To quit, enter \"leave\" or \"exit\".");
                while (isConnect) {
//                    Log.i(TAG, "isConnect = " + isConnect);
                    if (isReady) {
//                        Log.i(TAG, "isReady = " + isReady);

                        isReady = false;

                        if (input.equals("leave") || input.equals("exit"))
                            // We will send the leave message below.
                            break;

                        chat.sendMessage(input);
                    }

                    chronoface.processEvents();
                    // We need to sleep for a few milliseconds so we don't use 100% of the CPU.
                    Thread.sleep(10);
                }

                // The user entered the command to leave.
                chat.leave();
                // Wait a little bit to allow other applications to fetch the leave message.
                double startTime = getNowMilliseconds();
                while (true)
                {
                    if (getNowMilliseconds() - startTime >= 1000.0)
                        break;

                    chronoface.processEvents();
                    Thread.sleep(20);
                }

//                chat.chatEnd();
            }
            catch (Exception e) {
                Log.e(TAG, "exception: " + e.getMessage());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////
    /***
     * UI operation
     */

    public static void sendDebugMsg(String info) {
        Calendar ca = Calendar.getInstance();
        int hour = ca.get(Calendar.HOUR_OF_DAY);
        int min = ca.get(Calendar.MINUTE);
        int sec = ca.get(Calendar.SECOND);
        Message msg = new Message();
        msg.what = 400;
        msg.obj = hour + ":" + min + ":" + sec + " " + info;
        m_handler.sendMessage(msg);
    }
    private static Handler m_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                case 200:
                case 400: //just for debug
                    m_txMsg.append((String)msg.obj + "\n");
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    };

    public void showToast(String str) {
        Toast tst = Toast.makeText(getActivity(), str, Toast.LENGTH_LONG);
        tst.setGravity(Gravity.CENTER | Gravity.TOP, 0, 240);
        tst.show();
    }


}