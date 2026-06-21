package com.notificationfix;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView aboutText = findViewById(R.id.aboutText);
        aboutText.setText("Notification Fix Module\n\n" +
            "Version: 1.0\n\n" +
            "This module fixes notification lag when you have 10+ notifications.\n\n" +
            "Features:\n" +
            "- Batches rapid notifications\n" +
            "- Limits notification processing\n" +
            "- Optimizes memory usage\n\n" +
            "Enable this module in LSPosed Manager and reboot.");
    }
}
