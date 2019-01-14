package com.example.student.scout;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import android.telephony.SmsManager;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MyService extends Service {

    static MediaPlayer mediaPlayer;

    long stop, current, interval, stopAlarm, currentAlarm;
    boolean bAlarm = true;

    final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

//    static BluetoothSocket target1Socket, target2Socket, target3Socket;
    BluetoothSocket target1Socket, target2Socket, target3Socket;
    boolean bRead = true;

    MsgThread msg1Thread, msg2Thread, msg3Thread;
    Write1Thread write1Thread;
    Write2Thread write2Thread;
    Write3Thread write3Thread;

    Handler write1Handler, write2Handler, write3Handler;

    ReadThread read1Thread, read2Thread, read3Thread;
    StringBuffer sb;

    boolean bTarget1Read = true;
    boolean bTarget2Read = true;
    boolean bTarget3Read = true;
    boolean bBlue1 = false, bBlue2 = false, bBlue3 = false;


    final int SEND_MESSAGE = 100;
    final int SEND_2_MESSAGE = 200;
    final int SEND_3_MESSAGE = 300;
    final int RECEIVED_1_MESSAGE = 400;
    final int RECEIVED_2_MESSAGE = 500;
    final int RECEIVED_3_MESSAGE = 600;

    private MyDBHelper myDBHelper;
    Map<String, String> map;
    int lastCount, sum, avg;
    String state=" ";

    boolean bAnalyzeOn = true;

    public MyService() {
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            bAlarm = intent.getBooleanExtra("STOP", false);

        }
    };



    @Override
    public void onCreate() {

        super.onCreate();
        registerReceiver(receiver, new IntentFilter("scout"));

        myDBHelper = new MyDBHelper(this);
        map = new HashMap<>();

        // 서버와 클라이언트가 연결된 bluetoothSocket 객체
        target1Socket = BluetoothActivity.target1Socket;
        target2Socket = BluetoothActivity.target2Socket;
        target3Socket = BluetoothActivity.target3Socket;

        // 전달할 메시지를 저장할 Stringbuffer 객체
        sb = new StringBuffer();

        // 메시지 송수신을 위한 처리를 담당하는 스레드 생성 및 기동
        msg1Thread = new MsgThread(1);
        msg1Thread.start();

        msg2Thread = new MsgThread(2);
        msg2Thread.start();

        msg3Thread = new MsgThread(3);
        msg3Thread.start();

        Toast.makeText(this, "서비스가 연결되었습니다.", Toast.LENGTH_SHORT).show();

        Thread analyzeThread = new Thread(new Analyst());
        analyzeThread.start();

    }



        public void result(){
            if (write1Handler != null) {
                String result = selectAll();    // result 읽어오기 성공
                Message msg = new Message();
                msg.obj = result;

                Intent intent = new Intent(getApplicationContext(), MessageActivity.class);
                intent.putExtra("data", result);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                write1Handler.sendMessage(msg);
            }
        }

        public void reset(){
            SQLiteDatabase db = myDBHelper.getWritableDatabase();
            myDBHelper.onUpgrade(db, db.getVersion(), db.getVersion()+1);
            Toast.makeText(getApplicationContext(), "초기화 완료", Toast.LENGTH_SHORT).show();
        }


    // 기기와 블루투스 통신을 위한 소캣을 생성하고 데이터 송신을 위한 스레드를 생성 및 실행하는 스레드
    class TargetThread extends Thread {
        BluetoothSocket socket;
        BluetoothDevice device;

        public TargetThread(BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public void run() {
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                socket.connect();

                if(!bBlue1) {
                    target1Socket = socket;
                    if(msg1Thread == null) {
                        msg1Thread = new MsgThread(1);
                        msg1Thread.start();
                        bBlue1 = true;
                    }

                } else if (!bBlue2) {
                    target2Socket = socket;
                    if(msg1Thread == null) {
                        msg2Thread = new MsgThread(2);
                        msg2Thread.start();
                        bBlue2 = true;
                    }
                } else {
                    target3Socket = socket;
                    if(msg3Thread == null) {
                        msg3Thread = new MsgThread(3);
                        msg3Thread.start();
                        bBlue3 = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    // 데이터 송신 수신을 담당하는 스레드들을 실행하는 스레드
    class MsgThread extends Thread {
        int target_number;

        public MsgThread(int target_number) {
            this.target_number = target_number;
        }

        @Override
        public void run() {
            try {
                switch (target_number) {
                    case 1:
                        // 기존에 readThread 가 있다면 중지한다.
                        if(read1Thread != null) {
                            read1Thread.interrupt();
                        }
                        // 데이터를 수신하기 위한 readThread 생성
                        read1Thread = new ReadThread(target1Socket, 1);
                        read1Thread.start();

                        // 기존에 writeThread 가 있다면 중지한다.
                        if(write1Thread != null) {
                            // writeThread 내부에서 looper를 활용하고 있으므로
                            // looper를 종료해 주어야 한다.
                            write1Handler.getLooper().quit();
                        }

                        // 데이터를 송신하기 위한 writeThread 생성
                        write1Thread = new Write1Thread(target1Socket, 1);
                        write1Thread.start();
                        break;

                    case 2:
                        // 기존에 readThread 가 있다면 중지한다.
                        if(read2Thread != null) {
                            read2Thread.interrupt();
                        }
                        // 데이터를 수신하기 위한 readThread 생성
                        read2Thread = new ReadThread(target2Socket, 2);
                        read2Thread.start();


                        // 기존에 writeThread 가 있다면 중지한다.
                        if(write2Thread != null) {
                            // writeThread 내부에서 looper를 활용하고 있으므로
                            // looper를 종료해 주어야 한다.
                            write2Handler.getLooper().quit();
                        }

                        // 데이터를 송신하기 위한 writeThread 생성
                        write2Thread = new Write2Thread(target2Socket, 2);
                        write2Thread.start();

                        break;

                    case 3:
                        // 기존에 readThread 가 있다면 중지한다.
                        if(read3Thread != null) {
                            read3Thread.interrupt();
                        }
                        // 데이터를 수신하기 위한 readThread 생성
                        read3Thread = new ReadThread(target3Socket, 3);
                        read3Thread.start();


                        // 기존에 writeThread 가 있다면 중지한다.
                        if(write3Thread != null) {
                            // writeThread 내부에서 looper를 활용하고 있으므로
                            // looper를 종료해 주어야 한다.
                            write3Handler.getLooper().quit();
                        }

                        // 데이터를 송신하기 위한 writeThread 생성
                        write3Thread = new Write3Thread(target3Socket, 3);
                        write3Thread.start();

                        break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    // 송수신된 메시지를 화면에 TextView에 출력하기 위한 Handler
    Handler msgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case SEND_MESSAGE:  // READ THREAD, 센서값 읽어오기
                    try{
                        if(Integer.parseInt((String)msg.obj)>=10 && Integer.parseInt((String)msg.obj)<=10000){
                            sb.append("CO: "+(String)msg.obj+"\n");
                            map.put("time", DateFormat.getDateTimeInstance().format(new Date()));
                            map.put("value", (String)msg.obj);
                  //          Toast.makeText(getApplicationContext(), map.get("time")+", "+map.get("value"), Toast.LENGTH_SHORT).show();
                            insert();
                        }
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
                case SEND_2_MESSAGE:  // READ THREAD, 센서값 읽어오기
                    try{
                        if(Integer.parseInt((String)msg.obj)>=100 && Integer.parseInt((String)msg.obj)<=10000){
                            sb.append("CO: "+(String)msg.obj+"\n");
                            map.put("time", DateFormat.getDateTimeInstance().format(new Date()));
                            map.put("value", (String)msg.obj);
                   //         Toast.makeText(getApplicationContext(), map.get("time")+", "+map.get("value"), Toast.LENGTH_SHORT).show();
                            insert();
                        }
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
                case SEND_3_MESSAGE:  // READ THREAD, 센서값 읽어오기
                    try{
                        if(Integer.parseInt((String)msg.obj)>=100 && Integer.parseInt((String)msg.obj)<=10000){
                            sb.append("CO: "+(String)msg.obj+"\n");
                            map.put("time", DateFormat.getDateTimeInstance().format(new Date()));
                            map.put("value", (String)msg.obj);
                   //         Toast.makeText(getApplicationContext(), map.get("time")+", "+map.get("value"), Toast.LENGTH_SHORT).show();
                            insert();
                        }
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
                case RECEIVED_1_MESSAGE:  // DB 데이터 확인
                //    String result = selectAll();
                //    tv_log.setText(result);
                    break;
            }
        }
    };

    // 작성한 메시지를 전송하는 스레드, 기기 외부와 통신해야 하므로 Thread로 구성한다 _ 1
    class Write1Thread extends Thread {
        BluetoothSocket socket;
        OutputStream os = null;
        int target_number;

        public Write1Thread(BluetoothSocket socket, int target_number) {
            // 통신을 위한 bluetoothSocket 객체를 받는다.
            this.socket = socket;
            this.target_number = target_number;
            try {
                // bluetootsocket객체에서 OutputStream을 생성한다.
                os = socket.getOutputStream();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Looper.prepare();
            // 송신할 메시지를 받으면, 처리하는 핸들러
            write1Handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    try {
                        // 주어진 데이터를 OutputStream에 전달하여 상대측에 송신한다.
                        os.write(((String)msg.obj).getBytes());
                        os.flush();

                        // 전송한 데이터를 MessageActivity안의 TextView에 출력하기 위해 메시지를 전달한다.
                        Message msg_to_acti = new Message();
                        msg_to_acti.what = RECEIVED_1_MESSAGE;
                        msg_to_acti.obj = msg.obj;
                        msgHandler.sendMessage(msg_to_acti);
                    } catch (Exception e) {
                        e.printStackTrace();
                        write1Handler.getLooper().quit();
                    }
                }
            };

            Looper.loop();
        }
    }

    // 데이터를 아두이노에게 송신하는 스레드 2
    class Write2Thread extends Thread {
        BluetoothSocket socket;
        OutputStream os = null;
        int target_number;

        public Write2Thread(BluetoothSocket socket, int target_number) {
            // 통신을 위한 bluetoothSocket 객체를 받는다.
            this.socket = socket;
            this.target_number = target_number;
            try {
                // bluetootsocket객체에서 OutputStream을 생성한다.
                os = socket.getOutputStream();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Looper.prepare();
            // 메시지를 받으면, 처리하는 핸들러

            write2Handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    try {
                        // 주어진 데이터를 OutputStream에 전달하여 상대측에 송신한다.
                        os.write(((String)msg.obj).getBytes());
                        os.flush();

                        // 전송한 데이터를 MessageActivity안의 TextView에 출력하기 위해 메시지를 전달한다.
                        Message msg_to_acti = new Message();
                        msg_to_acti.what = RECEIVED_2_MESSAGE;
                        msg_to_acti.obj = msg.obj;
                        msgHandler.sendMessage(msg_to_acti);
                    } catch (Exception e) {
                        e.printStackTrace();
                        write2Handler.getLooper().quit();
                    }
                }
            };

            Looper.loop();
        }
    }

    // 데이터를 아두이노에게 송신하는 스레드 3
    class Write3Thread extends Thread {
        BluetoothSocket socket;
        OutputStream os = null;
        int target_number;

        public Write3Thread(BluetoothSocket socket, int target_number) {
            // 통신을 위한 bluetoothSocket 객체를 받는다.
            this.socket = socket;
            this.target_number = target_number;
            try {
                // bluetootsocket객체에서 OutputStream을 생성한다.
                os = socket.getOutputStream();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Looper.prepare();
            // 메시지를 받으면, 처리하는 핸들러

            write3Handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    try {
                        // 주어진 데이터를 OutputStream에 전달하여 상대측에 송신한다.
                        os.write(((String)msg.obj).getBytes());
                        os.flush();

                        // 전송한 데이터를 MessageActivity안의 TextView에 출력하기 위해 메시지를 전달한다.
                        Message msg_to_acti = new Message();
                        msg_to_acti.what = RECEIVED_3_MESSAGE;
                        msg_to_acti.obj = msg.obj;
                        msgHandler.sendMessage(msg_to_acti);
                    } catch (Exception e) {
                        e.printStackTrace();
                        write3Handler.getLooper().quit();
                    }
                }
            };

            Looper.loop();
        }
    }

    // 아두이노의 시리얼로 부터 메시지를 읽는 동작하는 스레드(스레드 1개로 구성)
    class ReadThread extends Thread {
        BluetoothSocket socket;
        BufferedInputStream bis = null;
        int target_number;
        boolean bRead;

        public ReadThread(BluetoothSocket socket, int target_number) {
            this.socket = socket;
            this.target_number = target_number;
            try {
                // bluetoothSocket에서 bufferedInputStream을 생성한다.
                bis = new BufferedInputStream(
                        socket.getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            if(target_number == 1) {
                bRead = bTarget1Read;
            } else if(target_number == 2) {
                bRead = bTarget2Read;
            } else if(target_number == 3) {
                bRead = bTarget3Read;
            }

            while(!Thread.currentThread().isInterrupted() && bRead) {

                try {
                    // 데이터를 임시로 저장할 버퍼를 만든다.
                    byte[] buf = new byte[1024];
                    // 버퍼에 데이터를 읽어온다.
                    int bytes = bis.read(buf);
                    // 읽어온 문자열 형태로 저장한다.
                    String read_str = new String(buf, 0, bytes);

                    // 읽어온 MessageActivity 안의 listview에 적용하기 위해 핸들러에 메시지를 전달한다
                    Message msg = new Message();
                    if(target_number == 1) {
                        msg.what = RECEIVED_1_MESSAGE;
                    } else if(target_number == 2) {
                        msg.what = RECEIVED_2_MESSAGE;
                    } else if(target_number == 3) {
                        msg.what = RECEIVED_3_MESSAGE;
                    }
                    msg.obj = read_str;
                    msgHandler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    bRead = false;
                }
            }
        }
    }




    // DB

    public class MyDBHelper extends SQLiteOpenHelper {
        public MyDBHelper(Context context){
            super(context, "groupCO", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            //    db.execSQL("CREATE TABLE groupCO (sTime TEXT NOT NULL PRIMARY KEY, sValue INTEGER NOT NULL);");
            db.execSQL("CREATE TABLE groupCO ( _ID INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, sTime TEXT NOT NULL, sValue INTEGER NOT NULL);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS groupCO;");
            onCreate(db);
        }
    }

    public void insert() {
        SQLiteDatabase db;
        ContentValues values;

        db = myDBHelper.getWritableDatabase();
        values = new ContentValues();
        values.put("sTime", map.get("time"));
        values.put("sValue", map.get("value"));
        db.insert("groupCO", null, values);
        db.close();
    }


    // 데이터 분석
    public class Analyst implements Runnable{

        Handler handler = new Handler();

        @Override
        public void run() {

            while(bAnalyzeOn){
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        analyze();
                    }
                });

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    }

    public void analyze(){
        SQLiteDatabase db = myDBHelper.getWritableDatabase();

        state=" ";
        sum = 0;
        avg = 0;
        Cursor cur;

        cur = db.rawQuery("select count (*) from groupCO;", null);
        while(cur.moveToNext()){
            lastCount = cur.getInt(0); // 현재 DB의 데이터 개수 (_ID)
        }

        int duration = lastCount-11;

        //       cur = db.rawQuery("SELECT sValue from groupCO where _ID >"+duration+";", null);
        cur = db.rawQuery("SELECT sValue from groupCO where _ID >"+duration+";", null);

        ArrayList<Integer> values = new ArrayList<>(); // 전체 데이터 담은 리스트
        while(cur.moveToNext()){
            values.add(cur.getInt(0));
        }

        ArrayList<Integer> valueA = new ArrayList<>(); // 50ppm 미만 : 좋음 SAFE
        ArrayList<Integer> valueB = new ArrayList<>(); // 50ppm 이상 200ppm 미만 : 보통 NORMAL
        ArrayList<Integer> valueC = new ArrayList<>(); // 200ppm 이상 400ppm 미만 : 주의 CAUTION
        ArrayList<Integer> valueD = new ArrayList<>(); // 400ppm 이상 800ppm 미만 : 경고 WARNING
        ArrayList<Integer> valueE = new ArrayList<>(); // 800ppm 이상 : 위험 DANGER

        String[] stateArr = {"SAFE", "NORMAL", "CAUTION", "WARNING", "DANGER" };

        ArrayList<ArrayList<Integer>> countGroupList = new ArrayList<>();
        countGroupList.add(valueA);
        countGroupList.add(valueB);
        countGroupList.add(valueC);
        countGroupList.add(valueD);
        countGroupList.add(valueE);


        // 구간별 데이터 할당
        for(int i=0; i<values.size(); i++){
            if (values.get(i)<50){
                valueA.add(values.get(i));
            } else if (values.get(i)<200){
                valueB.add(values.get(i));
            } else if (values.get(i)<400){
                valueC.add(values.get(i));
            } else if (values.get(i)<800){
                valueD.add(values.get(i));
            } else if (values.get(i)>=800){
                valueE.add(values.get(i));
            }
        }

        int countA = valueA.size();
        int countB = valueB.size();
        int countC = valueC.size();
        int countD = valueD.size();
        int countE = valueE.size();

        int[] countArr = {countA, countB, countC, countD, countE};

        // countArr를 merge sorting하여 가장 큰 count값 찾기 (가장 많은 데이터가 속한 그룹)
        countArr = merge_sort(countArr);

        int resultCount = countArr[countArr.length-1];
        ArrayList<Integer> resultGroup = new ArrayList<>();

        // 가장 큰 count값을 가진 그룹 찾기 (가장 많은 데이터가 속한 그룹)
        for(int i=0; i<countGroupList.size(); i++){
            if(resultCount == 0){
                resultGroup = countGroupList.get(0);
                state = stateArr[0];
            } else {
                if(resultCount == countGroupList.get(i).size()){
                    //    temp = countArr[i];
                    resultGroup = countGroupList.get(i);
                    state = stateArr[i];
                }
            }
        }



        // 가장 많은 데이터가 속한 그룹의 평균값 계산
        if(resultGroup.size()>0){
            for(int i=0; i<resultGroup.size(); i++){
                sum += resultGroup.get(i);
            }
            avg = sum/resultGroup.size();
            if(avg == 0){
                avg = resultGroup.get(0);
            }
        }

            SharedPreferences prfs = getSharedPreferences("contacts", 0);
            String tel1 = prfs.getString("tel1","0");
            String tel2 = prfs.getString("tel2", "0");
            String tel3 = prfs.getString("tel3", "0");
            String message = prfs.getString("message", "missing message");


    // CAUTION 단계시 메시지 및 팬 작동

        interval = 5000;

        if(state.equals("CAUTION")|| state.equals("WARNING" )|| state.equals("DANGER")){

            current = System.currentTimeMillis();

            if(current - stop >= interval){ // interval 이후에만 메시지 전송
        //        sendMessage(tel1, message);

                stop = System.currentTimeMillis();

                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();

            } else {
                Toast.makeText(getApplicationContext(), "waiting...", Toast.LENGTH_SHORT).show();
            }

            if(write2Handler != null) {
                Message msg = new Message();
                msg.obj = "a";
                write2Handler.sendMessage(msg);
   //             Toast.makeText(getApplicationContext(), "EXIT 환풍기 작동을 시작합니다.", Toast.LENGTH_SHORT).show();
            }

            if(write3Handler != null) {
                Message msg = new Message();
                msg.obj = "a";
                write3Handler.sendMessage(msg);
   //             Toast.makeText(getApplicationContext(), "ENTER 환풍기 작동을 시작합니다.", Toast.LENGTH_SHORT).show();
            }

        } else if(state.equals("SAFE") || state.equals("NORMAL")){

            if(write2Handler != null) {
                Message msg = new Message();
                msg.obj = "b";
                write2Handler.sendMessage(msg);
                Toast.makeText(getApplicationContext(), "EXIT 환풍기 작동이 중지되었습니다.", Toast.LENGTH_SHORT).show();
            }

            if(write3Handler != null) {
                Message msg = new Message();
                msg.obj = "b";
                write3Handler.sendMessage(msg);
                Toast.makeText(getApplicationContext(), "ENTER 환풍기 작동이 중지되었습니다.", Toast.LENGTH_SHORT).show();
            }

        }

        if(state.equals("CAUTION") || state.equals("WARNING") || state.equals("DANGER")){

            currentAlarm = System.currentTimeMillis();

            if(currentAlarm - stopAlarm >= interval){

                try{
                    mediaPlayer = MediaPlayer.create(MyService.this, R.raw.alarm);
                    mediaPlayer.start();
                } catch (Exception e){e.printStackTrace();}

                if(!bAlarm){
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    stopAlarm = System.currentTimeMillis();
                }
            } else {
                Toast.makeText(getApplicationContext(), "waiting...", Toast.LENGTH_SHORT).show();
            }
        }

        Toast.makeText(getApplicationContext(),
                "avg: "+avg+", state: "+state+", lastCount: "+lastCount,
                Toast.LENGTH_LONG).show();

        Intent intent  = new Intent("scout");
        intent.putExtra("Value", avg);
        intent.putExtra("State", state);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendBroadcast(intent);

    }

    public void sendMessage(String tel, String msg){

        if(tel != null){
            try{
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(tel, null, msg, null, null);
    //            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            } catch (Exception e){
                Toast.makeText(getApplicationContext(), "메시지 전송에 실패했습니다.", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    public int[] merge_sort (int[] arr){
        int n= arr.length;
        if(n==1) return arr;

        int[] arr_temp1 = new int[n/2];
        int[] arr_temp2 = new int[n-n/2];

        for (int i=0; i<n/2; i++){
            arr_temp1[i] = arr[i];
        }
        for(int i=0; i<n-n/2; i++){
            arr_temp2[i] = arr[i+n/2];
        }
        merge_sort(arr_temp1);
        merge_sort(arr_temp2);

        merge(arr_temp1, arr_temp2, arr);
        //    Toast.makeText(MessageActivity.this, arr[arr.length-1], Toast.LENGTH_LONG).show();

        return arr;
    }


    public static void merge (int[] arrA, int[] arrB, int[] arrC) {
        int iA = 0;
        int iB = 0;
        int iC = 0;

        while (iA < arrA.length) {
            if (iB < arrB.length) {
                if ( arrA[iA] < arrB[iB]) {
                    arrC[iC] = arrA[iA];
                    iA++;
                } else {
                    arrC[iC] = arrB[iB];
                    iB++;
                }
                iC++;
            } else {
                while (iA < arrA.length) {
                    arrC[iC] = arrA[iA];
                    iA++;
                    iC++;
                }
            }
        }

        while (iB < arrB.length) {
            arrC[iC] = arrB[iB];
            iB++;
            iC++;
        }
    }


    public String selectAll(){
        SQLiteDatabase db;
        ContentValues values;
        String[] projection = {"_ID", "sTime", "sValue"};
        Cursor cur;
        String result = "";

        db = myDBHelper.getReadableDatabase();
        cur = db.rawQuery("SELECT * FROM groupCO;", null);
        while(cur.moveToNext()){
            result += cur.getString(0)+", "+cur.getString(1)+", "+cur.getString(2)+"\n";
        }
        return result;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public class LocalBinder extends Binder{
        MyService getService(){
            return MyService.this;
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(getApplicationContext(), "서비스가 종료되었습니다.", Toast.LENGTH_SHORT).show();
        unregisterReceiver(receiver);
    }
}
