package com.notificationfix;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private SeekBar thresholdSeekBar;
    private TextView thresholdValue;
    private SeekBar maxPerAppSeekBar;
    private TextView maxPerAppValue;
    private SeekBar maxTotalSeekBar;
    private TextView maxTotalValue;
    private Switch enableGroupingSwitch;
    private Switch enableLimiterSwitch;
    private Switch enableMemorySwitch;
    private Switch enableUnisocSwitch;
    private Switch enableSmsFloodSwitch;
    private Switch enableDuplicateSwitch;
    private Switch enableCompressionSwitch;
    private SeekBar delaySeekBar;
    private TextView delayValue;
    private SeekBar cooldownSeekBar;
    private TextView cooldownValue;
    private TextView deviceInfoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("notification_fix_prefs", MODE_PRIVATE);

        initViews();
        loadSettings();
        showDeviceInfo();
    }

    private void initViews() {
        deviceInfoText = findViewById(R.id.deviceInfoText);

        // Threshold (2-20)
        thresholdSeekBar = findViewById(R.id.thresholdSeekBar);
        thresholdValue = findViewById(R.id.thresholdValue);
        thresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thresholdValue.setText(String.valueOf(progress + 2));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("threshold", seekBar.getProgress() + 2).apply();
                showSavedToast();
            }
        });

        // Max Per App (5-50)
        maxPerAppSeekBar = findViewById(R.id.maxPerAppSeekBar);
        maxPerAppValue = findViewById(R.id.maxPerAppValue);
        maxPerAppSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                maxPerAppValue.setText(String.valueOf((progress + 1) * 5));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("max_per_app", (seekBar.getProgress() + 1) * 5).apply();
                showSavedToast();
            }
        });

        // Max Total (20-200)
        maxTotalSeekBar = findViewById(R.id.maxTotalSeekBar);
        maxTotalValue = findViewById(R.id.maxTotalValue);
        maxTotalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                maxTotalValue.setText(String.valueOf((progress + 1) * 20));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("max_total", (seekBar.getProgress() + 1) * 20).apply();
                showSavedToast();
            }
        });

        // Batch Delay (500-5000ms)
        delaySeekBar = findViewById(R.id.delaySeekBar);
        delayValue = findViewById(R.id.delayValue);
        delaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                delayValue.setText((progress + 1) * 500 + "ms");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putLong("batch_delay", (seekBar.getProgress() + 1) * 500).apply();
                showSavedToast();
            }
        });

        // Cooldown (1-15 seconds)
        cooldownSeekBar = findViewById(R.id.cooldownSeekBar);
        cooldownValue = findViewById(R.id.cooldownValue);
        cooldownSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cooldownValue.setText((progress + 1) + "s");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putLong("cooldown", (seekBar.getProgress() + 1) * 1000).apply();
                showSavedToast();
            }
        });

        // Switches
        enableGroupingSwitch = findViewById(R.id.enableGroupingSwitch);
        enableGroupingSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("enable_grouping", checked).apply();
            showSavedToast();
        });

        enableLimiterSwitch = findViewById(R.id.enableLimiterSwitch);
        enableLimiterSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("enable_limiter", checked).apply();
            showSavedToast();
        });

        enableMemorySwitch = findViewById(R.id.enableMemorySwitch);
        enableMemorySwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("enable_memory_optimization", checked).apply();
            showSavedToast();
        });

        enableUnisocSwitch = findViewById(R.id.enableUnisocSwitch);
        enableUnisocSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("enable_unisoc_fix", checked).apply();
            showSavedToast();
        });

        enableSmsFloodSwitch = findViewById(R.id.enableSmsFloodSwitch);
        enableSmsFloodSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("enable_sms_flood", checked).apply();
            showSavedToast();
        });

        enableDuplicateSwitch = findViewById(R.id.enableDuplicateSwitch);
        enableDuplicateSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("enable_duplicate_block", checked).apply();
            showSavedToast();
        });

        enableCompressionSwitch = findViewById(R.id.enableCompressionSwitch);
        enableCompressionSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("enable_aggressive_compression", checked).apply();
            showSavedToast();
        });
    }

    private void loadSettings() {
        int threshold = prefs.getInt("threshold", 5);
        thresholdSeekBar.setProgress(threshold - 2);
        thresholdValue.setText(String.valueOf(threshold));

        int maxPerApp = prefs.getInt("max_per_app", 10);
        maxPerAppSeekBar.setProgress((maxPerApp / 5) - 1);
        maxPerAppValue.setText(String.valueOf(maxPerApp));

        int maxTotal = prefs.getInt("max_total", 50);
        maxTotalSeekBar.setProgress((maxTotal / 20) - 1);
        maxTotalValue.setText(String.valueOf(maxTotal));

        long delay = prefs.getLong("batch_delay", 2000);
        delaySeekBar.setProgress((int) (delay / 500) - 1);
        delayValue.setText(delay + "ms");

        long cooldown = prefs.getLong("cooldown", 5000);
        cooldownSeekBar.setProgress((int) (cooldown / 1000) - 1);
        cooldownValue.setText((cooldown / 1000) + "s");

        enableGroupingSwitch.setChecked(prefs.getBoolean("enable_grouping", true));
        enableLimiterSwitch.setChecked(prefs.getBoolean("enable_limiter", true));
        enableMemorySwitch.setChecked(prefs.getBoolean("enable_memory_optimization", true));
        enableUnisocSwitch.setChecked(prefs.getBoolean("enable_unisoc_fix", true));
        enableSmsFloodSwitch.setChecked(prefs.getBoolean("enable_sms_flood", true));
        enableDuplicateSwitch.setChecked(prefs.getBoolean("enable_duplicate_block", true));
        enableCompressionSwitch.setChecked(prefs.getBoolean("enable_aggressive_compression", true));
    }

    private void showDeviceInfo() {
        String info = "=== AGGRESSIVE MODE ===\n\n" +
            "Device: Symphony Z60+\n" +
            "Chipset: Unisoc T616\n" +
            "Android: 12\n" +
            "RAM: 6GB\n\n" +
            "Protection Level:\n" +
            "- 1000+ SMS: BLOCKED\n" +
            "- Rapid Fire: BLOCKED\n" +
            "- Duplicates: BLOCKED\n" +
            "- Memory: OPTIMIZED";
        deviceInfoText.setText(info);
    }

    private void showSavedToast() {
        Toast.makeText(this, "Settings saved! Restart LSPosed.", Toast.LENGTH_SHORT).show();
    }
}
