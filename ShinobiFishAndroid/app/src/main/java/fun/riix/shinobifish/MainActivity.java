package fun.riix.shinobifish;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 241;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(12, 18, 28));
        getWindow().setNavigationBarColor(Color.rgb(12, 18, 28));
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != getPackageManager().PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
        }
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(12, 18, 28));
        TextView heading = new TextView(this);
        heading.setText("SHINOBI FISH"); heading.setTextSize(28);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(Color.rgb(120, 236, 163));
        root.addView(heading);
        TextView subtitle = new TextView(this);
        subtitle.setText("Android fishing-timer prototype • Roblox\nNo root. Requires accessibility + screen recording permissions.");
        subtitle.setTextColor(Color.LTGRAY); subtitle.setTextSize(15);
        subtitle.setPadding(0, pad / 2, 0, pad);
        root.addView(subtitle);
        root.addView(button("1   ENABLE TOUCH AUTOMATION", v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(button("2   ALLOW SCREEN CAPTURE", v -> {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        }));
        root.addView(button("STOP SCREEN CAPTURE", v -> {
            stopService(new Intent(this, CaptureService.class));
            FishAccessibilityService.enabled = false;
            updateStatus();
        }));
        status = new TextView(this); status.setTextSize(15); status.setTextColor(Color.WHITE);
        status.setPadding(0, pad, 0, pad);
        root.addView(status);
        TextView instructions = new TextView(this);
        instructions.setText("SETUP\n\n1. Enable the 'Shinobi Fish' Accessibility service in Android Settings.\n\n2. Come back here and allow full-screen recording (choose Entire screen if prompted).\n\n3. Open Shinobi in Roblox, horizontally. Tap the floating FISH bubble.\n\n4. Calibrate GREEN (tap inside the green zone), HOOK, and WATER. Press START.\n\nThe macro detects the red needle crossing the green zone and taps HOOK. Auto-cast is optional. This is an experimental build; keep Roblox visible.");
        instructions.setTextColor(Color.LTGRAY); instructions.setTextSize(14);
        root.addView(instructions);
        setContentView(root);
    }

    private Button button(String text, View.OnClickListener click) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
        b.setLayoutParams(p); return b;
    }

    private void updateStatus() {
        if (status == null) return;
        status.setText("Touch automation: " + (FishAccessibilityService.instance != null ? "READY" : "NOT ENABLED") +
                "\nScreen capture: " + (CaptureService.isCapturing ? "READY" : "NOT STARTED"));
    }

    @Override protected void onResume() { super.onResume(); updateStatus(); }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQUEST_CAPTURE || result != RESULT_OK || data == null) return;
        Intent service = new Intent(this, CaptureService.class);
        service.putExtra(CaptureService.EXTRA_RESULT_CODE, result);
        service.putExtra(CaptureService.EXTRA_PROJECTION_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
        status.postDelayed(this::updateStatus, 1400);
    }
}
