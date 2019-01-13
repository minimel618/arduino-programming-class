package com.example.student.scout;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class ContactActivity extends AppCompatActivity {

    Button btn;
    EditText tel1, tel2, tel3, message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact);

        btn = (Button) findViewById(R.id.btnSend);
        tel1 = (EditText) findViewById(R.id.tel1);
        tel2 = (EditText) findViewById(R.id.tel2);
        tel3 = (EditText) findViewById(R.id.tel3);
        message = (EditText) findViewById(R.id.textMessage);

        /* Preference에 데이터 저장 */
        SharedPreferences prfs = getSharedPreferences("contacts", 0);
        SharedPreferences.Editor editor = prfs.edit();

        editor.putString("tel1", tel1.getText().toString());
        editor.putString("tel2", tel2.getText().toString());
        editor.putString("tel3", tel3.getText().toString());
        editor.putString("message", message.getText().toString());
        editor.apply();


        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Context context = getApplicationContext();

                Intent intent = new Intent(context, MainActivity.class);
                intent.putExtra("tel1", tel1.getText().toString().trim());
                intent.putExtra("tel2", tel2.getText().toString().trim());
                intent.putExtra("tel3", tel3.getText().toString().trim());
                intent.putExtra("message", message.getText().toString());
                startActivity(intent);
                finish();
            }
        });



    }

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




}
