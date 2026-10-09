package fun.riix.shinobifish;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FishAccessibilityService extends AccessibilityService {
    public static volatile FishAccessibilityService instance;
    public static volatile boolean enabled = false;
    private WindowManager windows;
    private View bubble;
    private View calibrationOverlay;
    private LinearLayout menu;
    private TextView label;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences settings;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        settings = getSharedPreferences("fish", MODE_PRIVATE);
        windows = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        main.post(this::showBubble);
    }

    private int dp(float n) { return (int) (getResources().getDisplayMetrics().density * n + .5f); }

    private void showBubble() {
        if (bubble != null || windows == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(5), dp(5), dp(5), dp(5));
        box.setBackgroundColor(Color.argb(233, 19, 31, 42));
        Button head = makeButton("🐟 FISH ▾", box);
        head.setOnClickListener(v -> menu.setVisibility(menu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(5), dp(3), dp(5), dp(5));
        box.addView(menu);

        label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setMaxWidth(dp(230));
        label.setText("Calibrate inside Roblox, then START");
        menu.addView(label);

        Button green = makeButton("Set GREEN zone", menu);
        green.setOnClickListener(v -> beginCalibration("bar", "Tap the CENTER of the green timing zone"));
        Button hook = makeButton("Set HOOK button", menu);
        hook.setOnClickListener(v -> beginCalibration("hook", "Tap the HOOK button"));
        Button water = makeButton("Set WATER cast", menu);
        water.setOnClickListener(v -> beginCalibration("water", "Tap a safe casting spot in the water"));

        CheckBox cast = new CheckBox(this);
        cast.setText("Auto-cast (experimental)");
        cast.setTextColor(Color.WHITE);
        cast.setTextSize(13);
        cast.setChecked(settings.getBoolean("autocast", true));
        cast.setOnCheckedChangeListener((c, checked) -> settings.edit().putBoolean("autocast", checked).apply());
        menu.addView(cast);

        Button start = makeButton("▶ START", menu);
        start.setOnClickListener(v -> {
            if (!CaptureService.isCapturing) {
                setLabel("Open the app and allow screen recording first.");
                return;
            }
            if (!calibrated("bar") || !calibrated("hook")) {
                setLabel("Calibrate GREEN and HOOK first.");
                return;
            }
            if (settings.getBoolean("autocast", true) && !calibrated("water")) {
                setLabel("Calibrate WATER or turn auto-cast off.");
                return;
            }
            CaptureService.resetTiming();
            enabled = true;
            setLabel("RUNNING — watching the red marker");
            menu.setVisibility(View.GONE);
        });
        Button stop = makeButton("■ STOP", menu);
        stop.setOnClickListener(v -> { enabled = false; setLabel("STOPPED"); });
        Button hide = makeButton("Minimize controls", menu);
        hide.setOnClickListener(v -> menu.setVisibility(View.GONE));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8); params.y = dp(110);
        windows.addView(box, params);
        bubble = box;
        menu.setVisibility(View.GONE);
    }

    private Button makeButton(String title, LinearLayout parent) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(12);
        button.setMinHeight(dp(35));
        button.setMinimumHeight(dp(35));
        button.setPadding(dp(8), dp(1), dp(8), dp(1));
        parent.addView(button);
        return button;
    }

    private boolean calibrated(String id) { return settings.contains(id + "X") && settings.contains(id + "Y"); }
    private void setLabel(String text) {
        main.post(() -> { if (label != null) label.setText(text); });
    }

    private void beginCalibration(String id, String instruction) {
        enabled = false;
        if (windows == null || calibrationOverlay != null) return;
        LinearLayout full = new LinearLayout(this);
        full.setOrientation(LinearLayout.VERTICAL);
        full.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        full.setBackgroundColor(Color.argb(70, 0, 0, 0));
        TextView hint = new TextView(this);
        hint.setText(instruction + "\n(Tap anywhere on screen to set location)");
        hint.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(17);
        hint.setPadding(dp(18), dp(15), dp(18), dp(15));
        hint.setBackgroundColor(Color.argb(235, 16, 54, 45));
        full.addView(hint);
        full.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
            int x = (int) event.getRawX(); int y = (int) event.getRawY();
            settings.edit().putInt(id + "X", x).putInt(id + "Y", y).apply();
            setLabel(id.toUpperCase() + " saved at " + x + ", " + y);
            if (calibrationOverlay != null) {
                windows.removeView(calibrationOverlay);
                calibrationOverlay = null;
            }
            menu.setVisibility(View.VISIBLE);
            return true;
        });
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        windows.addView(full, p);
        calibrationOverlay = full;
    }

    public void tap(int x, int y) {
        main.post(() -> {
            if (!enabled || instance != this) return;
            Path path = new Path(); path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 75);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            dispatchGesture(gesture, null, null);
        });
    }

    public void reportHit(int count) { setLabel("RUNNING — HOOK taps: " + count); }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { enabled = false; }
    @Override public void onDestroy() {
        enabled = false;
        if (windows != null) {
            if (calibrationOverlay != null) windows.removeView(calibrationOverlay);
            if (bubble != null) windows.removeView(bubble);
        }
        bubble = null; calibrationOverlay = null;
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
