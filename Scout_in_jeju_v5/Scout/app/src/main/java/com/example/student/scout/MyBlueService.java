package com.example.student.scout;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.student.scout.adapter.DeviceAdapter;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MyBlueService extends Service {

    long sleepInterval = 60000;

    Map<String, String> map = new HashMap<>();
    private MyDBHelper myDBHelper;

    //  private MyBlueService myService;
    private boolean mBound = false;

    final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    ArrayList<BluetoothDevice> device_list;
    DeviceAdapter deviceAdapter;
    BluetoothAdapter bluetoothAdapter;

    boolean bBlue1 = false, bBlue2 = false, bBlue3 = false;
    boolean bBlue = true;
    BluetoothDevice target1, target2, target3 = null;
    static BluetoothSocket target1Socket, target2Socket, target3Socket;
    ReadThread read1Thread, read2Thread, read3Thread;
    Write1Thread write1Thread;
    Write2Thread write2Thread;
    Write3Thread write3Thread;
    Handler write1Handler, write2Handler, write3Handler;

    final int SEND_1_MESSAGE = 100;
    final int RECEIVED_1_MESSAGE = 200;
    final int SEND_2_MESSAGE = 300;
    final int RECEIVED_2_MESSAGE = 400;
    final int SEND_3_MESSAGE = 500;
    final int RECEIVED_3_MESSAGE = 600;

    StringBuffer sb;

    boolean bTarget1Read = true;
    boolean bTarget2Read = true;
    boolean bTarget3Read = true;
    MsgThread msgTarget1Thread, msgTarget2Thread, msgTarget3Thread;

    static MediaPlayer mediaPlayer;

    int lastCount, sum, avg;
    String state = " ";

    boolean bService = true;
    boolean bAnalyzeOn = true;
    boolean bAlarm = true;
    boolean bVentilate = true;
    boolean bMessenger = true;

    Thread analyzeThread;
    Thread ventilateThread;
    Thread alarmThread;
    Thread messageThread;


    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            bAlarm = intent.getBooleanExtra("STOP", false);
            if(!bAlarm){
                alarmThread.interrupt();
                try {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            /*bService = intent.getBooleanExtra("stopThreads", false);
            if(!bService){
                alarmThread.interrupt();
                analyzeThread.interrupt();
                messageThread.interrupt();
                ventilateThread.interrupt();

             *//*   if(read1Thread != null){
                    read1Thread.interrupt();
                }
                if(read2Thread != null){
                    read2Thread.interrupt();
                }
                if(read3Thread != null){
                    read3Thread.interrupt();
                }
                if (write1Thread != null) {
                    // writeThread 내부에서 looper를 활용하고 있으므로
                    // looper를 종료해 주어야 한다.
                    write1Handler.getLooper().quit();
                }*//*

           //     stopSelf();

                bService = true;

            }*/

        }
    };


    public MyBlueService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        Log.d("MyBlueService", "onBind()");
        return new LocalBinder();
    }

    public class LocalBinder extends Binder {
        MyBlueService getService() {
            return MyBlueService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("MyBlueService", "onStartCommand()");
        registerReceiver(receiver, new IntentFilter("co"));

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("MyBlueService", "onDestroy()");
        Toast.makeText(getApplicationContext(), "서비스를 종료합니다.", Toast.LENGTH_SHORT).show();
        unregisterReceiver(receiver);

        // DB 초기화
        reset();

        try {
            if(mediaPlayer != null){
                mediaPlayer.stop();
                mediaPlayer.release();
            }
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("MyBlueService", "OnCreate()");

        Toast.makeText(getApplicationContext(), "서비스를 시작합니다.", Toast.LENGTH_SHORT).show();

        registerReceiver(receiver, new IntentFilter("co"));

        myDBHelper = new MyDBHelper(this);

        // 전달할 메시지를을 저장할 Stringbuffer 객체
        sb = new StringBuffer();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // 블루투스 지원 여부 검사
        if (bluetoothAdapter != null) {
            // 블루투스 활성화
            bluetoothAdapter.enable();

            // 검색된 블루투스 기기 정보를 저장하기 위한 Arraylist
            device_list = new ArrayList<BluetoothDevice>();

            // 검색된 블루투스 기기 정보를 listview에 표시하기 위한 Adapter 생성 및 등록
            deviceAdapter = new DeviceAdapter(
                    MyBlueService.this, R.layout.item_device,
                    device_list);

            // 스마트폰과 페어링 되어 있는 기기들의 목록을 만든다
            scanPairing();

        } else {
            Toast.makeText(getApplicationContext(), "블루투스를 지원하지 않음",
                    Toast.LENGTH_SHORT).show();
            stopSelf();
        }

        analyzeThread = new Thread(new MyBlueService.Analyst());
        ventilateThread = new Thread(new MyBlueService.Ventilator());
        alarmThread = new Thread(new MyBlueService.AlarmManager());
        messageThread = new Thread(new MyBlueService.Messenger());

        analyzeThread.start();
        ventilateThread.start();
        alarmThread.start();
        messageThread.start();

    }

    // 이전에 페어링된 기기 검색
    private void scanPairing() {
        Toast.makeText(getApplicationContext(), "이전 페어링된 기기를 검색함",
                Toast.LENGTH_SHORT).show();

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
        device_list.clear();

        if (devices.size() > 0) {
            Iterator<BluetoothDevice> iter = devices.iterator();
            while (iter.hasNext()) {
                BluetoothDevice d = iter.next();
                // 디바이스의 목록을 저장
                device_list.add(d);
                Log.d("BLUETEST", "name : " + d.getName() + " addr : " + d.getAddress());
            }

            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(getApplicationContext(),
                    "검색된 기기가 없습니다.",
                    Toast.LENGTH_SHORT).show();
        }


        TargetThread targetThread;

        BluetoothDevice[] device_arr = connectDevice(bluetoothAdapter, new String[]{"SENSOR", "EXIT", "ENTER"});

        try {
            target1 = device_arr[0];
            targetThread = new TargetThread(target1);
            Toast.makeText(getApplicationContext(), target1.getName() + "선택 완료", Toast.LENGTH_SHORT).show();
            targetThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            target2 = device_arr[1];
            targetThread = new TargetThread(target2);
            Toast.makeText(getApplicationContext(), target2.getName() + "선택 완료", Toast.LENGTH_SHORT).show();
            targetThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            target3 = device_arr[2];
            targetThread = new TargetThread(target3);
            Toast.makeText(getApplicationContext(), target3.getName() + "선택 완료", Toast.LENGTH_SHORT).show();
            targetThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private BluetoothDevice[] connectDevice(BluetoothAdapter ba, String[] devices_name) {
        BluetoothDevice[] result = null;

        if (ba == null) {
            Log.d("connectDevice", "No BluetoothAdapter");
            return null;
        }
        if (devices_name == null && devices_name.length <= 0) {
            Log.d("connectDevice", "No String array of Device name");
            return null;
        }

        result = new BluetoothDevice[devices_name.length];

        Set<BluetoothDevice> devices = ba.getBondedDevices();
        Log.d("connectDevice", "get Bonded Devices");
        if (devices.size() > 0) {
            Iterator<BluetoothDevice> iter = devices.iterator();
            while (iter.hasNext()) {
                BluetoothDevice d = iter.next();
                // 디바이스의 목록을 저장
                for (int i = 0; i < devices_name.length; i++) {
                    if (devices_name[i].equals(d.getName())) {
                        result[i] = d;
                        Log.d("connectDevice", "matched Device : " + d.getName());
                    }
                }
            }
            if (result.length != devices_name.length || result.length == 0) {
                Log.d("connectDevice", "No matched Device totally or partially");
                return null;
            }
        } else {
            Log.d("connectDevice", "No Bonded Device");
            return null;
        }

        return result;
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

                if (!bBlue1) {
                    target1Socket = socket;
                    if (msgTarget1Thread == null) {
                        msgTarget1Thread = new MsgThread(1);
                        msgTarget1Thread.start();
                        bBlue1 = true;
                    }

                } else if (!bBlue2) {
                    target2Socket = socket;
                    if (msgTarget2Thread == null) {
                        msgTarget2Thread = new MsgThread(2);
                        msgTarget2Thread.start();
                        bBlue2 = true;
                    }
                } else {
                    target3Socket = socket;
                    if (msgTarget3Thread == null) {
                        msgTarget3Thread = new MsgThread(3);
                        msgTarget3Thread.start();
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
                        if (read1Thread != null) {
                            read1Thread.interrupt();
                        }
                        // 데이터를 수신하기 위한 readThread 생성
                        read1Thread = new ReadThread(target1Socket, 1);
                        read1Thread.start();

                        // 기존에 writeThread 가 있다면 중지한다.
                        if (write1Thread != null) {
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
                        if (read2Thread != null) {
                            read2Thread.interrupt();
                        }
                        // 데이터를 수신하기 위한 readThread 생성
                        read2Thread = new ReadThread(target2Socket, 2);
                        read2Thread.start();

                        // 기존에 writeThread 가 있다면 중지한다.
                        if (write2Thread != null) {
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
                        if (read3Thread != null) {
                            read3Thread.interrupt();
                        }
                        // 데이터를 수신하기 위한 readThread 생성
                        read3Thread = new ReadThread(target3Socket, 3);
                        read3Thread.start();

                        // 기존에 writeThread 가 있다면 중지한다.
                        if (write3Thread != null) {
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


    // 데이터를 아두이노에게 송신하는 스레드 1
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
                // 메시지를 받으면, 처리하는 핸들러
                write1Handler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        try {
                            // 주어진 데이터를 OutputStream에 전달하여 상대측에 송신한다.
                            os.write(((String) msg.obj).getBytes());
                            os.flush();

                            // 전송한 데이터를 MessageActivity안의 TextView에 출력하기 위해 메시지를 전달한다.
                            Message msg_to_acti = new Message();
                            msg_to_acti.what = SEND_1_MESSAGE;
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
                            os.write(((String) msg.obj).getBytes());
                            os.flush();

                            // 전송한 데이터를 MessageActivity안의 TextView에 출력하기 위해 메시지를 전달한다.
                            Message msg_to_acti = new Message();
                            msg_to_acti.what = SEND_2_MESSAGE;
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
                            os.write(((String) msg.obj).getBytes());
                            os.flush();

                            // 전송한 데이터를 MessageActivity안의 TextView에 출력하기 위해 메시지를 전달한다.
                            Message msg_to_acti = new Message();
                            msg_to_acti.what = SEND_3_MESSAGE;
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

                if (target_number == 1) {
                    bRead = bTarget1Read;
                } else if (target_number == 2) {
                    bRead = bTarget2Read;
                } else if (target_number == 3) {
                    bRead = bTarget3Read;
                }

                while (!Thread.currentThread().isInterrupted() && bRead) {

                    try {
                        // 데이터를 임시로 저장할 버퍼를 만든다.
                        byte[] buf = new byte[1024];
                        // 버퍼에 데이터를 읽어온다.
                        int bytes = bis.read(buf);
                        // 읽어온 문자열 형태로 저장한다.
                        String read_str = new String(buf, 0, bytes);

                        // 읽어온 MessageActivity 안의 listview에 적용하기 위해 핸들러에 메시지를 전달한다
                        Message msg = new Message();
                        if (target_number == 1) {
                            msg.what = RECEIVED_1_MESSAGE;
                        } else if (target_number == 2) {
                            msg.what = RECEIVED_2_MESSAGE;
                        } else if (target_number == 3) {
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

    // 송수신된 메시지를 화면에 TextView에 출력하기 위한 Handler
    Handler msgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SEND_1_MESSAGE:
                    sb.setLength(0);
                    //        sb.append("send 1 > "+(String)msg.obj);

                    break;

                case RECEIVED_1_MESSAGE:
                    sb.setLength(0);
                    sb.append("receive 1 > " + (String) msg.obj);

                    try {
                        if (Integer.parseInt((String) msg.obj) >= 100 && Integer.parseInt((String) msg.obj) <= 10000) {
                            sb.append("CO: " + (String) msg.obj + "\n");
                            map.put("time", DateFormat.getDateTimeInstance().format(new Date()));
                            map.put("value", (String) msg.obj);
             //               Toast.makeText(getApplicationContext(), map.get("time") + ", " + map.get("value"), Toast.LENGTH_SHORT).show();
                            insert();

                            Intent coIntent = new Intent("co");
                            coIntent.putExtra("value", (String) msg.obj);
                            coIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            sendBroadcast(coIntent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (bBlue) {
                        Toast.makeText(getApplicationContext(), "블루투스 모듈이 모두 연결되었습니다.", Toast.LENGTH_SHORT).show();
                        bBlue = false;
                    }

                  /*  Intent intent = new Intent("co");
                    intent.putExtra("start", true);
                    sendBroadcast(intent);*/

                    break;

                case SEND_2_MESSAGE:
                    sb.setLength(0);
                    sb.append("send 2 > " + (String) msg.obj);
                    Toast.makeText(MyBlueService.this, sb, Toast.LENGTH_SHORT).show();
                    break;

                case RECEIVED_2_MESSAGE:
                    sb.setLength(0);
                    sb.append("receive 2 > " + (String) msg.obj);

                    try {
                        if (Integer.parseInt((String) msg.obj) >= 10 && Integer.parseInt((String) msg.obj) <= 10000) {
                            sb.append("CO: " + (String) msg.obj + "\n");
                            map.put("time", DateFormat.getDateTimeInstance().format(new Date()));
                            map.put("value", (String) msg.obj);
                //            Toast.makeText(getApplicationContext(), map.get("time") + ", " + map.get("value"), Toast.LENGTH_SHORT).show();
                            insert();
                            Intent coIntent = new Intent("co");
                            coIntent.putExtra("value", (String) msg.obj);
                            coIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            sendBroadcast(coIntent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (bBlue) {
                        Toast.makeText(getApplicationContext(), "블루투스 모듈이 모두 연결되었습니다.", Toast.LENGTH_SHORT).show();
                        bBlue = false;
                    }

                    break;

                case SEND_3_MESSAGE:
                    sb.setLength(0);
                    sb.append("send 3 > " + (String) msg.obj);
                    Toast.makeText(MyBlueService.this, sb, Toast.LENGTH_SHORT).show();
                    break;

                case RECEIVED_3_MESSAGE:
                    sb.setLength(0);
                    sb.append("receive 3 > " + (String) msg.obj);

                    try {
                        if (Integer.parseInt((String) msg.obj) >= 10 && Integer.parseInt((String) msg.obj) <= 10000) {
                            sb.append("CO: " + (String) msg.obj + "\n");
                            map.put("time", DateFormat.getDateTimeInstance().format(new Date()));
                            map.put("value", (String) msg.obj);
               //             Toast.makeText(getApplicationContext(), map.get("time") + ", " + map.get("value"), Toast.LENGTH_SHORT).show();
                            insert();
                            Intent coIntent = new Intent("co");
                            coIntent.putExtra("value", (String) msg.obj);
                            coIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            sendBroadcast(coIntent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (bBlue) {
                        Toast.makeText(getApplicationContext(), "블루투스 모듈이 모두 연결되었습니다.", Toast.LENGTH_SHORT).show();
                        bBlue = false;
                    }

                    break;
            }
        }
    };


/*
    // 연결 확립후 버튼을 누르면 명령(a, b)를 전송하는 리스너
    class BtnListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {

            switch(view.getId()) {
                case R.id.btn_blue1On:
                    if(write1Handler != null) {
                        Message msg = new Message();
                        msg.obj = "a";
                        write1Handler.sendMessage(msg);
                    }
                    break;

                case R.id.btn_blue1Off:
                    if(write1Handler != null) {
                        Message msg = new Message();
                        msg.obj = "b";
                        write1Handler.sendMessage(msg);
                        Intent intent = new Intent(getApplicationContext(), MyBlueService.class);
                        stopService(intent);
                    }
                    break;

                case R.id.btn_blue2On:
                    if(write2Handler != null) {
                        Message msg = new Message();
                        msg.obj = "a";
                        write2Handler.sendMessage(msg);
                    }
                    break;

                case R.id.btn_blue2Off:
                    if(write2Handler != null) {
                        Message msg = new Message();
                        msg.obj = "b";
                        write2Handler.sendMessage(msg);
                    }
                    break;

                case R.id.btn_blue3On:
                    if(write3Handler != null) {
                        Message msg = new Message();
                        msg.obj = "a";
                        write3Handler.sendMessage(msg);
                    }
                    break;

                case R.id.btn_blue3Off:
                    if(write3Handler != null) {
                        Message msg = new Message();
                        msg.obj = "b";
                        write3Handler.sendMessage(msg);
                    }
                    break;
            }
        }
    }*/


    // DB

    public class MyDBHelper extends SQLiteOpenHelper {
        public MyDBHelper(Context context) {
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
    public class Analyst implements Runnable {

        Handler handler = new Handler();

        @Override
        public void run() {
            Log.d("analyzeThread", "analyzeThread.run()");
            while (!Thread.currentThread().isInterrupted()) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        analyze();
                    }
                });

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.d("analyzeThread", "analyzeThread 종료");
        }
    }

    public void analyze() {

        SQLiteDatabase db = myDBHelper.getWritableDatabase();

        state = " ";
        sum = 0;
        avg = 0;
        Cursor cur;

        cur = db.rawQuery("select count (*) from groupCO;", null);
        while (cur.moveToNext()) {
            lastCount = cur.getInt(0); // 현재 DB의 데이터 개수 (_ID)
        }
        cur.close();

        int duration = lastCount - 11;

        //       cur = db.rawQuery("SELECT sValue from groupCO where _ID >"+lastcount+";", null);
        Cursor cur2 = db.rawQuery("SELECT sValue from groupCO where _ID >" + duration + ";", null);

        ArrayList<Integer> values = new ArrayList<>(); // 전체 데이터 담은 리스트
        while (cur2.moveToNext()) {
            values.add(cur2.getInt(0));
        }
        cur2.close();

        ArrayList<Integer> valueA = new ArrayList<>(); // 50ppm 미만 : 좋음 SAFE
        ArrayList<Integer> valueB = new ArrayList<>(); // 50ppm 이상 200ppm 미만 : 보통 NORMAL
        ArrayList<Integer> valueC = new ArrayList<>(); // 200ppm 이상 400ppm 미만 : 주의 CAUTION
        ArrayList<Integer> valueD = new ArrayList<>(); // 400ppm 이상 800ppm 미만 : 경고 WARNING
        ArrayList<Integer> valueE = new ArrayList<>(); // 800ppm 이상 : 위험 DANGER

        String[] stateArr = {"SAFE", "NORMAL", "CAUTION", "WARNING", "DANGER"};

        ArrayList<ArrayList<Integer>> countGroupList = new ArrayList<>();
        countGroupList.add(valueA);
        countGroupList.add(valueB);
        countGroupList.add(valueC);
        countGroupList.add(valueD);
        countGroupList.add(valueE);


        // 구간별 데이터 할당
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) < 50) {
                valueA.add(values.get(i));
            } else if (values.get(i) < 200) {
                valueB.add(values.get(i));
            } else if (values.get(i) < 400) {
                valueC.add(values.get(i));
            } else if (values.get(i) < 800) {
                valueD.add(values.get(i));
            } else if (values.get(i) >= 800) {
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

        int resultCount = countArr[countArr.length - 1];
        ArrayList<Integer> resultGroup = new ArrayList<>();

        // 가장 큰 count값을 가진 그룹 찾기 (가장 많은 데이터가 속한 그룹)
        for (int i = 0; i < countGroupList.size(); i++) {
            if (resultCount == 0) {
                resultGroup = countGroupList.get(0);
                state = stateArr[0];
            } else {
                if (resultCount == countGroupList.get(i).size()) {
                    //    temp = countArr[i];
                    resultGroup = countGroupList.get(i);
                    state = stateArr[i];
                }
            }
        }

        // 가장 많은 데이터가 속한 그룹의 평균값 계산
        if (resultGroup.size() > 0) {
            for (int i = 0; i < resultGroup.size(); i++) {
                sum += resultGroup.get(i);
            }
            avg = sum / resultGroup.size();
            if (avg == 0) {
                avg = resultGroup.get(0);
            }
        }

        Toast.makeText(getApplicationContext(),
                "avg: " + avg + ", state: " + state + ", lastCount: " + lastCount,
                Toast.LENGTH_LONG).show();

        Intent intent = new Intent("co");
        //    intent.putExtra("Value", avg);
        intent.putExtra("State", state);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendBroadcast(intent);

    }


    public class Ventilator implements Runnable {

        SharedPreferences prfs = getSharedPreferences("contacts", 0);
        String tel1 = prfs.getString("tel1", "0");
        String tel2 = prfs.getString("tel2", "0");
        String tel3 = prfs.getString("tel3", "0");
        String message = prfs.getString("message", "missing message");

        @Override
        public void run() {

            Log.d("ventilateThread", "ventilateThread.run()");
            while (!Thread.currentThread().isInterrupted()) {

                // CAUTION 단계시 팬 작동

                if (state.equals("CAUTION") || state.equals("WARNING") || state.equals("DANGER")) {

                    if (write2Handler != null) {
                        Message msg = new Message();
                        msg.obj = "a";
                        write2Handler.sendMessage(msg);
                    }

                    if (write3Handler != null) {
                        Message msg = new Message();
                        msg.obj = "a";
                        write3Handler.sendMessage(msg);
                    }

                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                } else if (state.equals("SAFE") || state.equals("NORMAL")) {

                    if (write2Handler != null) {
                        Message msg = new Message();
                        msg.obj = "b";
                        write2Handler.sendMessage(msg);
                    }

                    if (write3Handler != null) {
                        Message msg = new Message();
                        msg.obj = "b";
                        write3Handler.sendMessage(msg);
                    }

                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }

            }

            Log.d("ventilateThread", "ventilateThread 종료");

            // 스레드 종료시

            if (write2Handler != null) {
                Message msg = new Message();
                msg.obj = "b";
                write2Handler.sendMessage(msg);
            }

            if (write3Handler != null) {
                Message msg = new Message();
                msg.obj = "b";
                write3Handler.sendMessage(msg);
            }

            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public class AlarmManager implements Runnable {

        @Override
        public void run() {
            Log.d("alarmThread", "alarmThread.run()");

            try {
                while (!Thread.currentThread().isInterrupted()) {

                    if (state.equals("CAUTION") || state.equals("WARNING") || state.equals("DANGER")) {

                        mediaPlayer = MediaPlayer.create(MyBlueService.this, R.raw.alarm);
                        mediaPlayer.start();

                    } else if (state.equals("SAFE") || state.equals("NORMAL")) {
                        if (mediaPlayer != null) {
                            mediaPlayer.stop();
                            mediaPlayer.release();
                        }
                    }

                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                Log.d("alarmThread", "alarmThread 종료");
                // 알람을 끈 경우(stop버튼 클릭) 미디어 플레이어 멈춤, 1분동안 sleep.
                try {
                    if (mediaPlayer!= null) {
                        if(!mediaPlayer.isPlaying()){
                            mediaPlayer.stop();
                            mediaPlayer.release();
                        }
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }

    }


    public class Messenger implements Runnable {

        SharedPreferences prfs = getSharedPreferences("contacts", 0);
        String tel1 = prfs.getString("tel1", "0");
        String tel2 = prfs.getString("tel2", "0");
        String tel3 = prfs.getString("tel3", "0");
        String message = prfs.getString("message", "missing message");

        @Override
        public void run() {

            Log.d("messageThread", "messageThread.run()");

            while (!Thread.currentThread().isInterrupted()) {
                switch (state){
                    case "CAUTION":
                        sendMessage(tel1, message);
                        break;
                    case "WARNING":
                        sendMessage(tel1, message);
                        sendMessage(tel2, message);
                        break;
                    case "DANGER":
                        sendMessage(tel1, message);
                        sendMessage(tel2, message);
                        sendMessage(tel3, message);
                        break;
                    default:
                        try {
                            messageThread.sleep(sleepInterval);
                        } catch (InterruptedException e){
                            e.printStackTrace();
                        }

                }

            }

            Log.d("messageThread", "messageThread 종료");

        }
    }


    public void sendMessage(String tel, String msg) {

        if (tel != null) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(tel, null, msg, null, null);
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "메시지 전송에 실패했습니다.", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    public int[] merge_sort(int[] arr) {
        int n = arr.length;
        if (n == 1) return arr;

        int[] arr_temp1 = new int[n / 2];
        int[] arr_temp2 = new int[n - n / 2];

        for (int i = 0; i < n / 2; i++) {
            arr_temp1[i] = arr[i];
        }
        for (int i = 0; i < n - n / 2; i++) {
            arr_temp2[i] = arr[i + n / 2];
        }
        merge_sort(arr_temp1);
        merge_sort(arr_temp2);

        merge(arr_temp1, arr_temp2, arr);

        return arr;
    }


    public void merge(int[] arrA, int[] arrB, int[] arrC) {
        int iA = 0;
        int iB = 0;
        int iC = 0;

        while (iA < arrA.length) {
            if (iB < arrB.length) {
                if (arrA[iA] < arrB[iB]) {
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


    public String selectAll() {
        SQLiteDatabase db;
        ContentValues values;
        String[] projection = {"_ID", "sTime", "sValue"};
        Cursor cur;
        String result = "";

        db = myDBHelper.getReadableDatabase();
        cur = db.rawQuery("SELECT * FROM groupCO;", null);
        while (cur.moveToNext()) {
            result += cur.getString(0) + ", " + cur.getString(1) + ", " + cur.getString(2) + "\n";
        }
        cur.close();
        return result;
    }

    public void reset(){
        SQLiteDatabase db = myDBHelper.getWritableDatabase();
        myDBHelper.onUpgrade(db, db.getVersion(), db.getVersion()+1);
        Toast.makeText(getApplicationContext(), "초기화 완료", Toast.LENGTH_SHORT).show();
    }


}


