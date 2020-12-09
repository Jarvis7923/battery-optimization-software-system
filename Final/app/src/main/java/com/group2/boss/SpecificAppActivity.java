package com.group2.boss;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class SpecificAppActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_specific_app);

        Intent intent = getIntent();
        String name = intent.getStringExtra(MainActivity.SPECIFIC_APP_MESSAGE);
        String[] tokens = name.split(" ");
        TextView textView = findViewById(R.id.textAppName);
        textView.setText(tokens[0]);
    }
}