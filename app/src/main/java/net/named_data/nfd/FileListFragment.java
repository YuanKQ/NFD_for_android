package net.named_data.nfd;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by yuan on 16-4-18.
 */
public class FileListFragment extends Fragment {

    private ListView m_fileListView;
    private Switch m_connectSwitch;
    private EditText m_faceEdit;

    private FileListViewAdapter m_fileListAdapter;
    private ArrayList<HashMap<String, Object>> m_fileListContent = new ArrayList<HashMap<String, Object>>();
    private String m_faceAddr = "";
    private ArrayList<HashMap<String, String>> m_fileList = new ArrayList<HashMap<String, String>>();//The value will be assigned in the function "onClick" of Button m_btnReq
    private List<String> m_interestList; // The value will be assigned in the run() of FileLoadThread
    private File m_savePath = null;  //It will be initialized in the function "onCreate" of FileListenThread
    private boolean m_connect = false;

    public static final String TAG = "NFD";
    private static final int MAXLEN = 8000;
    private static final String PREFIX = "/ndn/org/test/FILE";

    private FileReqThread m_fRequester = null;
    private boolean m_isConnect;
    private ProgressDialog m_proDlg;
    private String m_fileName;
    private FileLoadThread m_fLoader;
    private FileListenThread m_fListener;

    public static Fragment newInstance() {
        return new FileListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        //make the file directory in the android phone
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File dir = new File(Environment.getExternalStorageDirectory().getAbsoluteFile()
                    + "/NFD/");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            m_savePath = dir;
        } else {
            m_savePath = null;
        }

        m_fListener = new FileListenThread();
        m_fListener.start();

        m_interestList = new ArrayList<String>();
    }


    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        View v = inflater.inflate(R.layout.activity_file, null);

        //Get UI Elements
        m_fileListView = (ListView) v.findViewById(R.id.lv_fileList);
        m_fileListAdapter = new FileListViewAdapter(getActivity(), m_fileList);
        m_fileListView.setAdapter(m_fileListAdapter);
        m_fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ListView listView = (ListView) adapterView;
                HashMap<String, Object> map = (HashMap<String, Object>) listView.getItemAtPosition(i);
                String fileName = (String) map.get(FileListViewAdapter.KEY_NAME);
                Log.i(TAG, "fileName is " + fileName);
                m_fileName = fileName;
                new AlertDialog.Builder(getActivity()).setMessage("Do you want to fetch the file " + fileName + " from " + m_faceAddr)
                        .setNegativeButton("NO", null)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        m_proDlg = ProgressDialog.show(getActivity(), "", "waiting...");
                                        //fetchFile(fileName);
                                        m_fLoader = new FileLoadThread();
                                        m_fLoader.start();
                                    }
                                }).show();
            }
        });

        m_faceEdit = (EditText) v.findViewById(R.id.ed_TargetFace);
        m_connectSwitch = (Switch) v.findViewById(R.id.switch_connect);
        m_connectSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    String tmp = m_faceEdit.getText().toString();
                    if (!tmp.equals("")) {
                        m_faceAddr = tmp;
                        Log.i(TAG, "m_faceAddr= " + m_faceAddr);
                        m_faceEdit.setEnabled(false);
                        m_isConnect = true;
                        m_fRequester = new FileReqThread();
                        m_fRequester.start();
                        m_fRequester.setReady();
                    } else {
                        m_connectSwitch.setChecked(false);
                        showToast("Please input the face address!");
                    }
                } else {
                    m_faceAddr = "";
                    m_faceEdit.setEnabled(true);
                    m_faceEdit.setText("");
                    m_fileList.clear();
//                    sendMsg(200, "");
                    m_isConnect = false;
                    Log.i(TAG, "m_faceAddr= " + m_faceAddr);
                }
            }
        });

        return v;
    }

    private void showToast(String msg) {
        Toast tst = Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG);
        tst.setGravity(Gravity.CENTER | Gravity.TOP, 0, 240);
        tst.show();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (m_fListener != null)
            m_fListener.listenFaceClose();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_file_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.file_list_refresh:
                if (m_faceAddr != "")
                    m_fRequester.setReady();
                else {
                    sendMsg(200, "");
                    Log.i(TAG, "m_fRequester is not existed!");
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    ///////////////////////////////////////////////////////////////
    /***
     * NFD Operation
     */

    private class FileReqThread extends Thread {
        public FileReqThread() {
            this.faceAddr = m_faceAddr;
            Log.i(TAG, "FileReqThread: faceAddr: " + faceAddr);
        }

        @Override
        public void run() {
            try {
                Log.i(TAG, "FileReqThread run()!");

                Face face = new Face(faceAddr);
                FileTimer timer = new FileTimer(0);
                while (m_isConnect) {
                    if (isReady) {
                        isReady = false;
                        String prefix = new String(PREFIX + "/" + System.currentTimeMillis());

                        Name name = new Name(prefix + "/fileList");
                        Log.i(TAG, "Express name " + name.toUri());
                        timer.startUp();
                        long interestId = face.expressInterest(name, timer, timer);
//                Log.i(TAG, "interestId: " + interestId);

                        while (timer.getCallbackCount() < 1) {
                            face.processEvents();
                            Thread.sleep(5);
                        }
                        face.removePendingInterest(interestId);
                    }
                    timer.setCallbackCount(0);
                }

                face.shutdown();
                Log.i(TAG, "reqFace close!");
            } catch (Exception e) {
                Log.i(TAG, "exception: " + e.getMessage());
                sendMsg(300, "exception: " + e.getMessage());
            }
        }

        private void setReady() {
            isReady = true;
        }

        private String faceAddr;
        private boolean isReady = false;
    }

    /****
     * This class will be used in the class FileLoadThread, type=1
     * and in the class FileReqThread, type=0
     */
    private class FileTimer implements OnData, OnTimeout {
        public FileTimer(int type) {
            callbackCount = 0;
            receiveID = 0;
            startTime = 0;
            fileSize = 0;
            this.type = type;

//            if (type == 1) {
//                //make the file directory in the android phone
//                if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
//                    File dir = new File(Environment.getExternalStorageDirectory().getAbsoluteFile()
//                            + "/NFD");
//
//                    if (!dir.exists()) {
//                        dir.mkdirs();
//                    }
//
//                    savePath = dir;
//                } else {
//                    savePath = null;
//                }
//                Log.i(TAG, "savePath = " + savePath);
//            }
        }

        @Override
        public void onData(Interest interest, Data data) {
            ++callbackCount;
            long elapsedTime = System.currentTimeMillis() - this.startTime;
            String name = data.getName().toUri();
            String contentStr = name + ": " + String.valueOf(elapsedTime) + " ms\n";
//            Log.i(TAG, "The receive threadName is: " + m_threadName);
            Log.i(TAG, ">> Content " + contentStr);

            Log.i(TAG, "type = " + type);
            if (type == 1) {
                writeToFile(data);
            } else {
                buildFileList(data);
            }

//            //print to the logcat
//            String contentStr = name + ": " + String.valueOf(elapsedTime) + " ms\n";
////            Log.i(TAG, "The receive threadName is: " + m_threadName);
//            Log.i(TAG, ">> Content " + contentStr);

            // Send a result to Screen
//            Message msg = new Message();
//            msg.what = 200; //  Result Code ex) Success code: 200 , FileList;
//            msg.obj = m_fileList;
        }

        private void buildFileList(Data data) {
            //sendDebugMsg(data.getContent().toString());
            m_fileList.clear();
            Log.i(TAG, "data:" + data.getContent().toString());
            String[] strs = data.getContent().toString().split("/");
            int i = 0;
            for (String str : strs) {
                if (i != 0) {
                    Log.i(TAG, str);
                    String[] tmp = str.split(":");
                    for (String ttmp: tmp)
                        Log.i(TAG, "ttmp:" + ttmp);
                    HashMap<String, String> map = new HashMap<String, String>();
                    map.put(FileListViewAdapter.KEY_NAME, tmp[0]);
                    map.put(FileListViewAdapter.KEY_SIZE, tmp[1]);
                    m_fileList.add(map);
                }
                ++ i;
            }

            sendMsg(200, "");
        }

        @Override
        public void onTimeout(Interest interest) {
            ++callbackCount;
            String contentStr = "Time out for interest " + interest.getName().toUri();
            //sendDebugMsg("Time out for interest " + interest.getName().toUri());
            Log.i(TAG, contentStr);

            // Send a result to Screen
//            Message msg = new Message();
//            msg.what = 400; // Result Code ex) Success code: 200 , Fail Code:
//            msg.obj = contentStr; // Result Object
//            m_handler.sendMessage(msg);
            sendMsg(400, contentStr);
        }

        private void writeToFile(Data data) {
//            sendDebugMsg("writeToFile();");
            String[] strs = data.getName().toUri().split("/");
            int len = strs.length;
            if (strs.length >= 3) {
                //Log.i(TAG, "strs[len-2]=" + strs[len - 2]);
                if (strs[len - 2].matches("^S\\d+")) {
                    int segment = Integer.parseInt(strs[len - 2].substring(1));
                    //Log.i(TAG, "write segmemt NO." + segment);
                    if (segment == receiveID) {
                        ++receiveID;
                        //Log.i(TAG, "receiveID:" + receiveID);
                        //write to the file
                        String fileName = strs[len - 1];
                        if (segment == 0) {
                            fileRecv = new File(m_savePath, fileName);
                            if (!fileRecv.exists()) {
                                //Log.i(TAG, "!fileRecv.exists(): " + fileRecv.getName());
                                try {
                                    fileRecv.createNewFile();
                                } catch (IOException e) {
                                    Log.e(TAG, "Fail to create new file" + fileName + " :" + e.getMessage());
                                }
                            } else {
                                fileName = fileName.substring(0, fileName.indexOf(".")) + "-"
                                        + System.currentTimeMillis()
                                        + fileName.substring(fileName.indexOf("."));
                                try {
                                    fileRecv = new File(m_savePath, fileName);
                                    fileRecv.createNewFile();
                                    //Log.i(TAG, "createNewFile: " + fileRecv.getName());
                                } catch (IOException e) {
                                    Log.e(TAG, "Fail to create new file" + fileName + " :" + e.getMessage());
                                }
                            }
                        }
                        if (fileRecv.exists()) {
                            try {
                                ByteBuffer byteBuffer = data.getContent().buf();
                                byte[] buf = new byte[byteBuffer.remaining()];
                                byteBuffer.get(buf);
//                            sendDebugMsg("Start writing..." + new String(buf));
//                            sendDebugMsg("Start writing...");
//                            sendDebugMsg(new String(data.getContent().buf().array()));
                                RandomAccessFile rAccessFile = new RandomAccessFile(fileRecv, "rw");
                                long fileLength = rAccessFile.length();
                                rAccessFile.seek(fileLength);
                                rAccessFile.write(buf);
                                rAccessFile.close();
                                Log.i(TAG, "End writing to " + fileRecv.getName());

                                if (fileSize == segment + 1)
                                    sendMsg(201, "The file has saved as " + fileRecv.getName() + " in " + fileRecv.getAbsolutePath());
                            } catch (IOException e) {
                                // TODO: handle exception
                                Log.i(TAG, "IOException in writeToFile:" + e.getMessage());
                            }
                        } else
                            Log.i(TAG, "Fail to create the receiving file!");
                    }
                } else {
                    String content = data.getContent().toString();
                    fileSize = Integer.parseInt(content.substring(content.indexOf("=") + 1));
                }
            }
        }

        public void startUp() {
            startTime = System.currentTimeMillis();
        }

        public int getReceiveID() {
            return receiveID;
        }

        public int getFileSize() {
            return fileSize;
        }

        public int getCallbackCount() {
            return callbackCount;
        }

        public void setCallbackCount(int callbackCount) {
            this.callbackCount = callbackCount;
        }

//        public File getSavePath() {
//            return savePath;
//        }

        private File fileRecv;
        private int callbackCount;
        private int receiveID;
        private long startTime;
        //        private File savePath = null;
        private int fileSize;
        private int type;
    }


    /****
     * This class is used to load files.
     */
    private class FileLoadThread extends Thread {
        /***
         * The constructor of the class of FileLoadThread
         * type:0, create the face in local host; else, create the face in the the address of m_faceAddr;
         */
        public FileLoadThread() {
            faceAddr = m_faceAddr;
            this.filename = m_fileName;
            send_id = 0;
            Log.i(TAG, "faceAddr=" + faceAddr + " filename=" + filename);
        }

        @Override
        public void run() {
            try {
                Log.i(TAG, "FileLoader run()!");
                String prefix = PREFIX + "/" + filename;
                Name name = new Name(prefix);
                Log.i(TAG, "Express name " + prefix);

//                int i = 0;
//                Log.i(TAG, "m_interestList:");
//                for (String str: m_interestList) {
//                    Log.i(TAG, "[" + i + "]" + str);
//                    ++i;
//                }

                /***
                 * If the consumer sends the same interest,
                 * since the correspondent response to the interest
                 * has been saved in the local nfd CS(content store),
                 * the consumer doesn't need to send the interest to the remote face(content provider),
                 * but just fetch the response from the local CS.
                 */
                if (m_interestList.contains(prefix)) {
                    Log.i(TAG, "Create local face!");
                    loadFace = new Face();
                }
                else {
                    Log.i(TAG, "Create remote face: " + faceAddr);
                    loadFace = new Face(faceAddr);
                    m_interestList.add(prefix);
                }
                fileTimer = new FileTimer(1);
                fileTimer.startUp();

                //send an interest packet to get the size of the file

                loadFace.expressInterest(name, fileTimer, fileTimer);
                while (fileTimer.getCallbackCount() < 1) {
                    loadFace.processEvents();
                    Thread.sleep(5);
                }
                fileTimer.setCallbackCount(0);  //Important!!!!!

                //send interest packets to get the file
                int tmpSize = fileTimer.getFileSize();
                //sendDebugMsg("callback=" + fileTimer.getCallbackCount() + " fileSize=" + tmpSize);
//                while (send_id < tmpSize) {
//                    String interest = prefix + "/T" + tmpSize + "/S" + send_id + "/" + filename;
//                    Name name1 = new Name(interest);
//                    sendDebugMsg("Express name " + name1.toUri());
//                    //fileTimer.startUp();
//                    face.expressInterest(name1, fileTimer, fileTimer);
//                    ++ send_id;
//                }
//                while (fileTimer.getCallbackCount() < tmpSize) {
//                    face.processEvents();
//                    Thread.sleep(5);
//                }
                if (tmpSize == 0)
                    sendMsg(201, "The file doesn't exist anymore!\nPlease refresh the FILELIST.");

                while (send_id < tmpSize) {
                    //fileTimer.getCallbackCount() < (tmpSize + 5) permit to resend 5 times
                    String interest = prefix + "/T" + tmpSize + "/S" + send_id + "/" + filename;
                    Name name1 = new Name(interest);
                    Log.i(TAG, "Express name " + name1.toUri());
                    fileTimer.startUp();
                    int curCallback = fileTimer.getCallbackCount();
                    loadFace.expressInterest(name1, fileTimer, fileTimer);
                    if (send_id == fileTimer.getReceiveID()) {
                        ++send_id;
                    }
                    //sendDebugMsg("send_id:" + send_id + " receiveID:" + fileTimer.getReceiveID());
                    do {
                        Thread.sleep(5);
                        loadFace.processEvents();
                    } while (fileTimer.getCallbackCount() < curCallback + 1);
                    //sendDebugMsg("curCallback:" + curCallback + " callBackCount:" + fileTimer.getCallbackCount());
                }

                if (fileTimer.getCallbackCount() == (tmpSize + 5)) {
                    new AlertDialog.Builder(getActivity()).setTitle("Fail to receive the file " + filename +
                            "Are you want to delete the file?")
                            .setNegativeButton("No", null)
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    File fileRecv = new File(m_savePath, filename);
                                    if (fileRecv.exists()) {
                                        fileRecv.delete();
                                        Log.i(TAG, "Delete " + filename + "successfully!");
                                        Toast.makeText(getActivity(), "Delete " + filename + "successfully!",
                                                Toast.LENGTH_LONG);
                                    } else {
                                        Toast.makeText(getActivity(), "Fail to delete " + filename + "!",
                                                Toast.LENGTH_LONG);
                                    }
                                }
                            }).show();
                }

                loadFaceClose();
            } catch (Exception e) {
                Log.e(TAG, "Exception in FileLoadThread: " + e.getMessage());
            }
        }

        public void loadFaceClose() {
            if (loadFace != null) {
                //loadFace.removePendingInterest(interestId);
                loadFace.shutdown();
                Log.i(TAG, "loadFaceClose()!");
            }
        }
        private Face loadFace;
        private String faceAddr;
        private String filename;
        private int send_id;
        private FileTimer fileTimer;
    }

    private class Echo implements OnInterestCallback, OnRegisterFailed {
        public Echo(KeyChain keyChain, Name certificateName) {
            keyChain_ = keyChain;
            certificateName_ = certificateName;
        }

        public void
        onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                   InterestFilter filter) {
            // Make and sign a Data packet.
            Data data = new Data(interest.getName());
//            String content = "<<Echo " + interest.getName().toString();
//            data.setContent(new Blob(content));
            //Log.i(TAG, ">> onInterest threadName:" + m_threadName);
            Log.i(TAG, ">> Interest: " + interest.getName().toUri());
            String[] strs = interest.getName().toUri().split("/");
//            int i = 0;
//            for (String str: strs) {
//                Log.i(TAG, "[" + i + "] " + str);
//                i ++;
//            }
            //The order of the judgement can't be changed
            int len = strs.length;
            if (strs[len - 1].equals("fileList")) {
                //Log.i(TAG, getReqFileDir());
                data.setContent(new Blob(getReqFileDir()));
            } else if (strs[len - 2].equals("FILE")) {
                data.setContent(new Blob(getReqFileSize(strs[len - 1])));
            } else {
                byte[] content1 = new byte[MAXLEN];
                File f = new File(m_savePath, strs[len - 1]);
                if (f.exists()) {
                    try {
                        RandomAccessFile rAccessFile = new RandomAccessFile(f, "r");
                        long curLen = Integer.parseInt(strs[len - 2].substring(1)) * MAXLEN;
                        if (curLen <= rAccessFile.length()) {
                            rAccessFile.seek(curLen);
                            rAccessFile.read(content1);
                        }


                    } catch (Exception e) {
                        Log.e(TAG, "Exception happened in readFile: " + e.getMessage());
                    }
                }
//                sendDebugMsg(">>Send content:" + new String(content1));
                data.setContent(new Blob(content1));
                //sendDebugMsg("FileContent=" + new String(getReqFileContent(strs[len - 1], strs[len - 2])));
                //data.setContent(new Blob(getReqFileContent(strs[len-1], strs[len-2])));
            }

            String content = "<<" + interest.getName().toString();
            //data.setContent(new Blob(content));

//            Message msg = new Message();
//            msg.what = 201; // Result Code ex) Success code: 200 , Fail Code:
//            // 400 ...
//            msg.obj = content; // Result Object
//            m_handler.sendMessage(msg);

            try {
                keyChain_.sign(data, certificateName_);
            } catch (net.named_data.jndn.security.SecurityException e) {
                throw new Error("SecurityException in sign: " + e.getMessage());
            }
            try {
                face.putData(data);
                //sendDebugMsg(content);
            } catch (IOException ex) {
                Log.i(TAG, "Echo: IOException in sending data " + ex.getMessage());
            }
        }

        private String getReqFileSize(String str) {
            File f = new File(m_savePath, str);
            long size = 0;
            if (f.exists()) {
                if (f.length() % MAXLEN != 0)
                    size = (f.length() + MAXLEN) / MAXLEN;
                else
                    size = f.length() / MAXLEN;
            }

//            Log.i(TAG, str + "length is: " + f.length());
            //sendDebugMsg("FileSize=" + size);

            return new String("Total=" + size);
        }

        public void
        onRegisterFailed(Name prefix) {
            //++responseCount_;
            Log.i(TAG, "Register failed for prefix " + prefix.toUri());

//            Message msg = new Message();
//            msg.what = 300; //300: failed to register the prefix
//            msg.obj = "Register failed for prefix " + prefix.toUri();
//            m_handler.sendMessage(msg);
            sendMsg(300, "Register failed for prefix " + prefix.toUri() + ". Please retart the App!");
        }

        private String getReqFileDir() {
            //Log.i(TAG, "getReqFileDir()!");
            String fileList = new String();
            File[] files = m_savePath.listFiles();
            for (File file : files) {
                fileList = fileList + "/" + file.getName() + ":" + switchFileSize(file.length());
                //Log.i(TAG, "In getReqFileDir: " + file.getName());
            }

            Log.i(TAG, ">>fileList:" + fileList);
            return fileList;
        }
        private String switchFileSize(long size) {
            double tmp = size;
            String[] unit = {"B", "KB", "MB", "GB", "TB"};
            int i = 0;
            while (tmp > 1024){
                Log.i(TAG, "tmp=" + tmp);
                tmp = tmp / 1024;
                i ++;
            }
            Log.i(TAG, "result=" + tmp + unit[i]);
            return tmp + unit[i];
        }

//        private byte[] getReqFileContent(String filename, String segment) {
//            byte[] content = new byte[MAXLEN];
//            File f = new File(m_savePath, filename);
//            if (f.exists()) {
//                try {
//                    RandomAccessFile rAccessFile = new RandomAccessFile(f, "r");
//                    long curLen = Integer.parseInt(segment.substring(1)) * MAXLEN;
//                    if (curLen <= rAccessFile.length()) {
//                        rAccessFile.seek(curLen);
//                        rAccessFile.read(content);
//                    }
//
//
//                } catch (Exception e) {
//                    Log.e(TAG, "Exception happened in readFile: " + e.getMessage());
//                }
//            }
//            return content;
//        }

        KeyChain keyChain_;
        Name certificateName_;
        //int responseCount_ = 0;
    }

    private class FileListenThread extends Thread {
        public void run() {
            try {
                Log.i(TAG, "FileListenThread run()!");

                listenFace = new Face();
                // For now, when setting face.setCommandSigningInfo, use a key chain with
                //   a default private key instead of the system default key chain. This
                //   is OK for now because NFD is configured to skip verification, so it
                //   ignores the system default key chain.
                MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
                MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
                KeyChain keyChain = new KeyChain
                        (new IdentityManager(identityStorage, privateKeyStorage),
                                new SelfVerifyPolicyManager(identityStorage));
                keyChain.setFace(listenFace);

                // Initialize the storage.
                Name keyName = new Name("/testname/DSK-123");
                Name certificateName = keyName.getSubName(0, keyName.size() - 1).append
                        ("KEY").append(keyName.get(-1)).append("ID-CERT").append("0");
//                Log.i(TAG, certificateName.toString());
                identityStorage.addKey(keyName, KeyType.RSA, new Blob(DEFAULT_RSA_PUBLIC_KEY_DER, false));
                privateKeyStorage.setKeyPairForKeyName
                        (keyName, KeyType.RSA, DEFAULT_RSA_PUBLIC_KEY_DER, DEFAULT_RSA_PRIVATE_KEY_DER);

                listenFace.setCommandSigningInfo(keyChain, certificateName);

                Echo echo = new Echo(keyChain, certificateName);
                Name prefix = new Name(PREFIX);
//                Log.i(TAG, "Register prefix  " + prefix.toUri());
                registerPrefixId = listenFace.registerPrefix(prefix, echo, echo);

//                Name prefix1 = new Name("/");
//                Log.i(TAG, "Register prefix  " + prefix1);
//                face.registerPrefix(prefix1, echo, echo);

//                Name prefix2 = new Name("/testecho");
//                Log.i(TAG, "Register prefix  " + prefix2);
//                face.registerPrefix(prefix2, echo, echo);

                // The main event loop.
                // Wait to receive one interest for the prefix.
                //while (echo.responseCount_ < 100) {
                while (true) {
                    listenFace.processEvents();
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                Log.i(TAG, "exception: " + e.getMessage());
                sendMsg(300, "exception: " + e.getMessage());
            }
        }

        public void listenFaceClose() {
            if (listenFace != null) {
                //listenFace.removeRegisteredPrefix(registerPrefixId);
                listenFace.shutdown();
                Log.i(TAG, "listenFaceClose()!");
            }
        }

        private long registerPrefixId;
        private Face listenFace;
    }

    private static ByteBuffer toBuffer(int[] array) {
        ByteBuffer result = ByteBuffer.allocate(array.length);
        for (int i = 0; i < array.length; ++i)
            result.put((byte) (array[i] & 0xff));

        result.flip();
        return result;
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

    ////////////////////////////////////////////////////////////
    /***
     * UI process
     */
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
                case 200: //200: fileList
                    Log.i(TAG, "200!");
                   //m_fileListView.setAdapter(null);  //remove the previous data
                    m_fileListAdapter.notifyDataSetChanged();
                    break;

                case 201: //201: file operation
                case 400: //400: timeout
                    if (m_proDlg != null) {
                        m_proDlg.dismiss();
                    }
                    showToast((String)msg.obj);
                    break;

                case 300:  //300: fail to register
                    m_connectSwitch.setChecked(false);
                    showToast((String)msg.obj);
                    break;

                default:
                    super.handleMessage(msg);

            }
        }
    };
}
