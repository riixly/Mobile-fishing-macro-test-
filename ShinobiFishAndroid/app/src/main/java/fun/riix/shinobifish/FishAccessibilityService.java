package fun.riix.shinobifish;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
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
import android.widget.SeekBar;
import android.widget.TextView;

public class FishAccessibilityService extends AccessibilityService {
    public static volatile FishAccessibilityService instance;
    public static volatile boolean enabled = false;
    private WindowManager windows;
    private View bubble;
    private View calibrationOverlay;
    private LinearLayout menu;
    private TextView label;
    private TextView sizeLabel;
    private LinearLayout bubbleLayout;
    private LinearLayout sizeRow;
    private CheckBox castCheck;
    private WindowManager.LayoutParams bubbleParams;
    private final java.util.ArrayList<Button> controls = new java.util.ArrayList<>();
    private int sizePercent = 80;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences settings;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        settings = getSharedPreferences("fish", MODE_PRIVATE);
        sizePercent = Math.max(60, Math.min(130, settings.getInt("menuSize", 80)));
        windows = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        main.post(this::showBubble);
    }

    private int dp(float n) { return (int) (getResources().getDisplayMetrics().density * n + .5f); }

    private void showBubble() {
        if (bubble != null || windows == null) return;
        LinearLayout box = new LinearLayout(this);
        bubbleLayout = box;
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.argb(233, 19, 31, 42));
        Button head = makeButton("🐟 FISH ▾", box);
        head.setOnClickListener(v -> menu.setVisibility(menu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        head.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int originalRight, originalTop;
            boolean dragging;
            final int slop = android.view.ViewConfiguration.get(FishAccessibilityService.this).getScaledTouchSlop();
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        originalRight = bubbleParams.x; originalTop = bubbleParams.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = e.getRawX() - downX;
                        float deltaY = e.getRawY() - downY;
                        if (Math.abs(deltaX) > slop || Math.abs(deltaY) > slop) dragging = true;
                        if (dragging && bubble != null) {
                            DisplayMetrics screen = new DisplayMetrics();
                            windows.getDefaultDisplay().getRealMetrics(screen);
                            int maxRight = Math.max(0, screen.widthPixels - Math.max(bubble.getWidth(), dp(55)));
                            int maxTop = Math.max(0, screen.heightPixels - dp(50));
                            bubbleParams.x = Math.max(0, Math.min(maxRight, originalRight - Math.round(deltaX)));
                            bubbleParams.y = Math.max(0, Math.min(maxTop, originalTop + Math.round(deltaY)));
                            windows.updateViewLayout(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default: return false;
                }
            }
        });

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(5), dp(3), dp(5), dp(5));
        box.addView(menu);

        label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setMaxWidth(dp(230));
        label.setText("Drag FISH to move. Calibrate, then START.");
        menu.addView(label);

        Button green = makeButton("Set GREEN zone", menu);
        green.setOnClickListener(v -> beginCalibration("bar", "Tap the CENTER of the green timing zone"));
        Button hook = makeButton("Set HOOK button", menu);
        hook.setOnClickListener(v -> beginCalibration("hook", "Tap the HOOK button"));
        Button water = makeButton("Set WATER cast", menu);
        water.setOnClickListener(v -> beginCalibration("water", "Tap a safe casting spot in the water"));

        CheckBox cast = new CheckBox(this);
        castCheck = cast;
        cast.setText("Auto-cast (experimental)");
        cast.setTextColor(Color.WHITE);
        cast.setChecked(settings.getBoolean("autocast", true));
        cast.setOnCheckedChangeListener((c, checked) -> settings.edit().putBoolean("autocast", checked).apply());
        menu.addView(cast);

        sizeLabel = new TextView(this);
        sizeLabel.setTextColor(Color.WHITE);
        menu.addView(sizeLabel);
        SeekBar sizeSlider = new SeekBar(this);
        sizeSlider.setMax(70);
        sizeSlider.setProgress(sizePercent - 60);
        menu.addView(sizeSlider);
        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int progress, boolean user) {
                if (!user) return;
                sizePercent = progress + 60;
                settings.edit().putInt("menuSize", sizePercent).apply();
                applyMenuSize();
            }
            @Override public void onStartTrackingTouch(SeekBar seek) { }
            @Override public void onStopTrackingTouch(SeekBar seek) { }
        });

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

        bubbleParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        // Default to the water side so the fishing timing bar remains visible.
        bubbleParams.gravity = Gravity.TOP | Gravity.RIGHT;
        bubbleParams.x = dp(12); bubbleParams.y = dp(85);
        menu.setVisibility(View.GONE);
        applyMenuSize();
        windows.addView(box, bubbleParams);
        bubble = box;
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
        controls.add(button);
        return button;
    }

    private void applyMenuSize() {
        float scale = sizePercent / 100f;
        if (bubbleLayout == null || menu == null) return;
        int pad = dp(5 * scale);
        bubbleLayout.setPadding(pad, pad, pad, pad);
        menu.setPadding(pad, dp(3 * scale), pad, pad);
        menu.setLayoutParams(new LinearLayout.LayoutParams(dp(255 * scale), LinearLayout.LayoutParams.WRAP_CONTENT));
        for (Button button : controls) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * scale);
            int minHeight = dp(35 * scale);
            button.setMinHeight(minHeight);
            button.setMinimumHeight(minHeight);
            button.setPadding(dp(7 * scale), dp(1), dp(7 * scale), dp(1));
        }
        if (label != null) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * scale);
            label.setMaxWidth(dp(240 * scale));
        }
        if (castCheck != null) castCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * scale);
        if (sizeLabel != null) {
            sizeLabel.setText("Menu size: " + sizePercent + "% (drag slider)");
            sizeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12 * scale);
        }
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
