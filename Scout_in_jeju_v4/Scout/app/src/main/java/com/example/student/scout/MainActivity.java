package com.example.student.scout;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    boolean bPerm;
    boolean bService = true;

    ImageButton btnInfo, btnOwl, btnService;
    TextView textCO, textState;

    boolean bAlarm = false;
    long startTime;

    String tel1, tel2, tel3, message;

    String co, state;

    BroadcastReceiver receiver;

    MyBlueService myBlueService;
    private boolean mBound = false;

    ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myBlueService = ((MyBlueService.LocalBinder)service).getService(); // 서비스 객체 얻기
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.SEND_SMS } , MODE_PRIVATE);

        // 권한 관련 설정
        setPermission(new String[] {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
        });

        Intent intent = new Intent(MainActivity.this, MyBlueService.class);
        startService(intent);

        btnInfo = (ImageButton) findViewById(R.id.btnInfo);
        btnOwl = (ImageButton) findViewById(R.id.btnOwl);
        btnService = (ImageButton) findViewById(R.id.btnService);
        textCO = (TextView) findViewById(R.id.textCO);
        textState = (TextView) findViewById(R.id.textState);


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("일산화탄소 농도별 증상");
        builder.setIcon(R.drawable.owl);
        LayoutInflater factory = LayoutInflater.from(MainActivity.this);
        final View view = factory.inflate(R.layout.sample, null);
        builder.setView(view);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

       btnInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.show();
            }
        });

        btnOwl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnOwl.setVisibility(View.INVISIBLE);
                Intent intent = new Intent("co");
                intent.putExtra("STOP", false);
                sendBroadcast(intent);

            }
        });

        btnService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(bService){
                    Intent stopThreadsIntent = new Intent("co");
                    stopThreadsIntent.putExtra("stopThreads", false);
                    sendBroadcast(stopThreadsIntent);

                    Intent serviceIntent = new Intent(MainActivity.this, MyBlueService.class);
                    stopService(serviceIntent);
                    bService = false;
                    btnService.setColorFilter(Color.GRAY);
                    if(mBound) {
                        unbindService(mConn);   // 서비스와 연결 해제
                        mBound = false;
                    }
                } else {
                    Intent serviceIntent = new Intent(MainActivity.this, MyBlueService.class);
                    startService(serviceIntent);
                    bService = true;
                    btnService.setColorFilter(Color.GREEN);
                }
            }
        });


        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                co = intent.getStringExtra("value");
       //         Toast.makeText(getApplicationContext(), "MainActivity: "+co, Toast.LENGTH_LONG).show();
                state = intent.getStringExtra("State");

                if(co != null){
                    textCO.setText(co+"ppm");
                }

                if(state != null){
                    textState.setText(state);

                    switch (state){
                        case "SAFE":
                            textState.setTextColor(Color.GREEN);
                            break;
                        case "NORMAL":
                            textState.setTextColor(Color.LTGRAY);
                            break;
                        case "CAUTION":
                            textState.setTextColor(Color.YELLOW);
                            btnOwl.setVisibility(View.VISIBLE);

                            // 알람, tel1 에 sms 보내기
                            break;
                        case "WARNING":
                            textState.setTextColor(Color.MAGENTA);
                            btnOwl.setVisibility(View.VISIBLE);
                            // 알람, tel1과 tel2에 sms 보내기
                            break;

                        case "DANGER":
                            textState.setTextColor(Color.RED);
                            btnOwl.setVisibility(View.VISIBLE);
                            // 알람, tel1과 tel2, tel3에 sms 보내기
                            break;
                    }

               }
            }
        };

        registerReceiver(receiver, new IntentFilter("co"));

    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        registerReceiver(receiver, new IntentFilter("co"));
    }
/*
    public void sendMessage(String tel, String msg){

        if(tel != null){
            try{
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(tel, null, msg, null, null);
              Toast.makeText(this, tel+" 번호로 알림 메시지를 전송했습니다.", Toast.LENGTH_SHORT).show();
            } catch (Exception e){
                Toast.makeText(getApplicationContext(), "메시지 전송에 실패했습니다.", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }*/


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.itemContact:
                Intent intent1 = new Intent(getApplicationContext(),ContactActivity.class);
                startActivity(intent1);
                break;
            case R.id.itemBluetooth:
                Intent intent2 = new Intent(getApplicationContext(),BluetoothActivity.class);
                startActivity(intent2);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setPermission(String[] perm) {
        boolean bPerm = false;

        if(ContextCompat.checkSelfPermission(getApplicationContext(), perm[0])
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(getApplicationContext(), perm[1])
                        == PackageManager.PERMISSION_GRANTED) {
            bPerm = true;
        }

        if(!bPerm) {
            ActivityCompat.requestPermissions(
                    this, perm, 200);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean bPerm = true;
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 200 && grantResults.length > 0) {
            for(int i = 0; i < grantResults.length; i++) {
                if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    bPerm = false;
                }
            }
            if(bPerm) {

            }
        }
        this.bPerm = bPerm;
    }

}
