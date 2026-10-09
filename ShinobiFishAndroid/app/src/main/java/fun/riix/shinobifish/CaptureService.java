package fun.riix.shinobifish;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_PROJECTION_DATA = "projection_data";
    public static volatile boolean isCapturing = false;
    private static volatile long resetEpoch = 0;
    private final Object lifecycle = new Object();
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader frames;
    private HandlerThread workerThread;
    private Handler worker;
    private int width, height, density;
    private volatile long lastHitAt = 0, lastBarAt = 0, lastCastAt = 0;
    private volatile int hits = 0;
    private volatile boolean priorInGreen = false;
    private volatile long handledEpoch = 0;
    private SharedPreferences settings;
    private static final int NOTIF_ID = 4321;
    private static final String CHANNEL = "shinobi-capture";

    public static void resetTiming() { resetEpoch++; }

    @Override public void onCreate() {
        super.onCreate();
        settings = getSharedPreferences("fish", MODE_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Fishing screen capture", NotificationManager.IMPORTANCE_LOW));
        workerThread = new HandlerThread("shinobi-screen"); workerThread.start();
        worker = new Handler(workerThread.getLooper());
        lastBarAt = android.os.SystemClock.elapsedRealtime();
    }

    @SuppressWarnings("deprecation")
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf(); return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildNotification());
        if (projection != null) return START_NOT_STICKY;
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent permissionData;
        if (Build.VERSION.SDK_INT >= 33)
            permissionData = intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent.class);
        else permissionData = intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
        if (code == 0 || permissionData == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(code, permissionData);
            if (projection == null) { stopSelf(); return START_NOT_STICKY; }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { isCapturing = false; FishAccessibilityService.enabled = false; stopSelf(); }
                @Override public void onCapturedContentResize(int w, int h) {
                    if (w > 0 && h > 0) worker.post(() -> resizeCapture(w, h));
                }
            }, new Handler(Looper.getMainLooper()));
            DisplayMetrics metrics = readMetrics(); density = metrics.densityDpi;
            width = metrics.widthPixels; height = metrics.heightPixels;
            synchronized (lifecycle) {
                frames = makeReader(width, height);
                display = projection.createVirtualDisplay("Shinobi Fish live pixels", width, height, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, frames.getSurface(), null, worker);
            }
            isCapturing = true;
        } catch (Exception e) {
            android.util.Log.e("ShinobiFish", "Cannot start screen recording", e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, CaptureService.class).setAction("STOP");
        PendingIntent stopAction = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openAction = PendingIntent.getActivity(this, 3, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Shinobi Fish")
                .setContentText("Screen analysis active; use floating FISH to start / stop")
                .setOngoing(true)
                .setContentIntent(openAction)
                .addAction(android.R.drawable.ic_media_pause, "Stop capture", stopAction)
                .build();
    }

    @SuppressWarnings("deprecation")
    private DisplayMetrics readMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }

    private ImageReader makeReader(int w, int h) {
        ImageReader reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(this::onImage, worker);
        return reader;
    }

    private void resizeCapture(int w, int h) {
        synchronized (lifecycle) {
            if (display == null || (w == width && h == height)) return;
            ImageReader old = frames;
            ImageReader replacement = makeReader(w, h);
            try {
                display.resize(w, h, density);
                display.setSurface(replacement.getSurface());
                frames = replacement; width = w; height = h;
                if (old != null) old.close();
                FishAccessibilityService.enabled = false; // Require re-calibration after orientation changes.
            } catch (Exception e) {
                replacement.close();
                android.util.Log.e("ShinobiFish", "Resize failed", e);
            }
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        worker.post(() -> {
            DisplayMetrics m = readMetrics();
            resizeCapture(m.widthPixels, m.heightPixels);
        });
    }

    private void onImage(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || !FishAccessibilityService.enabled) return;
            FishAccessibilityService input = FishAccessibilityService.instance;
            if (input == null) return;
            final int w = image.getWidth(), h = image.getHeight();
            int barX = settings.getInt("barX", -1), barY = settings.getInt("barY", -1);
            int hookX = settings.getInt("hookX", -1), hookY = settings.getInt("hookY", -1);
            if (barX < 0 || hookX < 0) return;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int rowStride = plane.getRowStride(), pixelStride = plane.getPixelStride();
            FishingDetector.Pixels pixels = (x, y) -> {
                int offset = y * rowStride + x * pixelStride;
                // ImageReader RGBA_8888 stores bytes as R, G, B, A.
                int r = buf.get(offset) & 255, g = buf.get(offset + 1) & 255, b = buf.get(offset + 2) & 255;
                return 0xff000000 | (r << 16) | (g << 8) | b;
            };
            FishingDetector.Reading result = FishingDetector.analyze(pixels, w, h, barX, barY);
            long now = android.os.SystemClock.elapsedRealtime();
            if (handledEpoch != resetEpoch) {
                priorInGreen = false; lastHitAt = 0; lastBarAt = now; lastCastAt = now;
                handledEpoch = resetEpoch;
            }
            if (result.barVisible) lastBarAt = now;
            if (result.inGreen && !priorInGreen && now - lastHitAt >= 950) {
                lastHitAt = now; hits++;
                input.tap(hookX, hookY);
                input.reportHit(hits);
            }
            priorInGreen = result.inGreen;
            // Experimental cast loop: do not cast while the timing minigame is visible.
            if (!result.barVisible && settings.getBoolean("autocast", true) &&
                    now - lastBarAt > 6500 && now - lastCastAt > 7000) {
                int waterX = settings.getInt("waterX", -1), waterY = settings.getInt("waterY", -1);
                if (waterX >= 0 && waterY >= 0) {
                    input.tap(waterX, waterY);
                    lastCastAt = now;
                }
            }
        } catch (Exception e) {
            android.util.Log.w("ShinobiFish", "Frame skipped", e);
        } finally {
            if (image != null) image.close();
        }
    }

    @Override public void onDestroy() {
        FishAccessibilityService.enabled = false;
        isCapturing = false;
        synchronized (lifecycle) {
            if (display != null) { display.release(); display = null; }
            if (frames != null) { frames.close(); frames = null; }
            if (projection != null) {
                MediaProjection old = projection; projection = null;
                try { old.stop(); } catch (Exception ignored) { }
            }
        }
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
