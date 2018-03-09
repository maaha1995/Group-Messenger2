package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final Map<String, String> processid = new HashMap<String, String>();
    static final int SERVER_PORT = 10000;
    int count=-1;
    int proposed_id_local = 0;
    int failed_avd = -1;
    boolean avd = false;

    PriorityQueue<Result> queue1 = new PriorityQueue<Result>();
    Map<String,Result> mymap = new HashMap<String, Result>();

    class Result implements Comparable<Result>{
        float id;
        String message;
        Boolean flag;

        public Result(float id, String message, Boolean flag){
            this.id = id;
            this.message = message;
            this.flag = false;
        }

        public int compareTo(Result r){
            if(id > r.id){
                return 1;
            }
            else if(id < r.id){
                return -1;
            }
            else if (Math.abs(id - r.id) < 0.01){
                return 0;
            }
            return 0;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processid.put(REMOTE_PORT0, "0.0");
        processid.put(REMOTE_PORT1, "0.1");
        processid.put(REMOTE_PORT2, "0.2");
        processid.put(REMOTE_PORT3, "0.3");
        processid.put(REMOTE_PORT4, "0.4");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.d(TAG, "Can't create a ServerSocket");
            return;
        }

        final EditText editText = (EditText) findViewById(R.id.editText1);
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        editText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {

                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String msg = editText.getText().toString();
                    editText.setText("");

                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                    return true;
                }
                return false;
            }
        });

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");// This is one way to reset the input box.
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);


            }
        });
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        private Uri buildUri(String scheme, String authority) {

            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }
        private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            try {
                while (true) {
                    Socket socket = serverSocket.accept();

                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String rec_proposed_msg = in.readLine();
                    if (rec_proposed_msg == null)
                        return null;
                    String[] rec_pmg = rec_proposed_msg.split("#");
                    if (rec_pmg[2].equals("proposal")) {

                        String rec_mg = rec_pmg[0];
                        String process_id = rec_pmg[1];

                        float proposed_id = Float.valueOf(Integer.toString((int) proposed_id_local) + process_id.substring(1, 3));
                        proposed_id_local = Math.round(proposed_id) + 1;
                        StringBuilder sb1 = new StringBuilder(String.valueOf(proposed_id));
                        sb1.append("#");
                        sb1.append("ack");

                        Result obj = new Result(proposed_id, rec_mg, false);

                        queue1.add(obj);
                        mymap.put(rec_mg, obj);


                        String ackToSend = sb1.toString() + "\n";
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        out.writeBytes(ackToSend);
                        out.flush();
                    }

                    if (rec_pmg[2].equals("agreement")) {

                        String rec_mg = rec_pmg[0];

                        Float agreed_id = Float.parseFloat(rec_pmg[1]);

                        Result current_obj = mymap.get(rec_mg);
                        queue1.remove(current_obj);
                        current_obj.id = agreed_id;
                        current_obj.flag = true;

                        proposed_id_local = Math.round(agreed_id) + 1;
                        queue1.add(current_obj);

                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        out.writeBytes("received_agreement\n");
                        out.flush();
                    }

                    if(rec_pmg[2].equals("failure")){

                        String avd_number = rec_pmg[1];
                        failed_avd = Integer.parseInt(avd_number);
                        avd = true;

                        Iterator<Result> itr = queue1.iterator();

                        while(itr.hasNext()){
                            Result obj = itr.next();
                            float id = obj.id;
                            String id1 = Float.toString(id);

                            String[] id2 = id1.split("\\.");

                            if(id2[1].equals(avd_number)){
                                queue1.remove(obj);
                            }
                        }

                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        out.writeBytes("received_failure\n");
                        out.flush();

                    }

                    while (!queue1.isEmpty() && queue1.peek().flag == true) {

                        Result obj_to_display = queue1.poll();

                        String msgToDisplay = obj_to_display.message;

                        publishProgress(msgToDisplay);
                        count++;
                        ContentValues contentValues1 = new ContentValues();
                        contentValues1.put("key", Integer.toString(count));
                        contentValues1.put("value", msgToDisplay);
                        getContentResolver().insert(mUri, contentValues1);

                    }
                    socket.close();
                }
            }

            catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onProgressUpdate(String...strings) {

            String strReceived = strings[0].trim();
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append("\t\n"+strReceived);
            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

                String[] remotePort = {REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};
                float global_count = 0;
                String msgToSend = msgs[0];
                try {
                    for (int i = 0; i < remotePort.length; i++) {
                        if (i == failed_avd) {
                            continue;
                        }
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(remotePort[i]));

                            StringBuilder sb = new StringBuilder(msgToSend);
                            sb.append('#');
                            String portno = msgs[1];
                            String pid = processid.get(portno);
                            sb.append(pid);
                            sb.append('#');
                            sb.append("proposal");
                            String pmsgToSend = sb.toString() + "\n";

                            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                            out.writeBytes(pmsgToSend);
                            out.flush();

                            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            String rec_proposed_id = in.readLine();

                            if (rec_proposed_id == null || rec_proposed_id.isEmpty()) {

                                in.close();
                                socket.close();
                                throw new NullPointerException();
                            }
                            else {
                                String[] rec_pid = rec_proposed_id.split("#");
                                if (rec_pid[1].equals("ack")) {
                                    global_count = Math.max(global_count, Float.parseFloat(rec_pid[0]));
                                    in.close();
                                    socket.close();
                                }
                            }
                        } catch (Exception e) {
                            failed_avd = i;

                        }

                    }

                    if (failed_avd != -1 && avd == false) {
                        try {
                            for (int i = 0; i < remotePort.length; i++) {

                                if (i == failed_avd) {
                                    continue;
                                }
                                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(remotePort[i]));

                                StringBuilder sb3 = new StringBuilder("Message");
                                sb3.append('#');
                                sb3.append(Integer.toString(failed_avd));
                                sb3.append('#');
                                sb3.append("failure");
                                DataOutputStream out =
                                        new DataOutputStream(socket.getOutputStream());
                                out.writeBytes(sb3.toString() + "\n");
                                out.flush();

                                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                String rec_ack = in.readLine();

                                if (rec_ack.equals("received_failure")) {
                                    in.close();
                                    socket.close();
                                }

                                avd = true;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    for (int i = 0; i < remotePort.length; i++) {

                        try {
                            if (i == failed_avd) {
                                continue;
                            }

                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(remotePort[i]));
                            StringBuilder sb2 = new StringBuilder(msgToSend);
                            sb2.append('#');
                            sb2.append(String.valueOf(global_count));
                            sb2.append('#');
                            sb2.append("agreement");
                            String amsgToSend = sb2.toString() + "\n";
                            DataOutputStream out =
                                    new DataOutputStream(socket.getOutputStream());
                            out.writeBytes(amsgToSend);
                            out.flush();


                            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            String rec_ack = in.readLine();
                            if (rec_ack == null || rec_ack.isEmpty()) {

                                in.close();
                                socket.close();
                                throw new NullPointerException();

                            } else {
                                if (rec_ack.equals("received_agreement")) {
                                    in.close();
                                    socket.close();
                                }
                            }
                        } catch (Exception e) {
                            failed_avd = i;
                        }
                    }

                    if (failed_avd != -1 && avd == false) {
                        try {
                            for (int i = 0; i < remotePort.length; i++) {
                                if (i == failed_avd) {
                                    continue;
                                }
                                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(remotePort[i]));

                                StringBuilder sb3 = new StringBuilder("Message");
                                sb3.append('#');
                                sb3.append(Integer.toString(failed_avd));
                                sb3.append('#');
                                sb3.append("failure");
                                DataOutputStream out =
                                        new DataOutputStream(socket.getOutputStream());
                                out.writeBytes(sb3.toString() + "\n");
                                out.flush();

                                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                String rec_ack = in.readLine();

                                if (rec_ack.equals("received_failure")) {
                                    in.close();
                                    socket.close();
                                }

                                avd = true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch(Exception e){
                    e.printStackTrace();
                }
            return null;
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
