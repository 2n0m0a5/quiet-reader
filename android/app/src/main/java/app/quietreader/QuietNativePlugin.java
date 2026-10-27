package app.quietreader;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.OpenableColumns;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.WindowManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Quiet's own Android features, used from src/app/native.ts:
 *  - "Open with Quiet" and "Share to Quiet": incoming book files are copied into the app's
 *    cache and announced to the web app with an "incomingFile" event.
 *  - Turning pages with the volume keys ("volumeKey" events), when the web app asks for it.
 *  - Keeping the screen on while reading.
 *  - Screen rotation lock, small vibrations (haptics), and the battery level for the reading clock.
 */
@CapacitorPlugin(name = "QuietNative")
public class QuietNativePlugin extends Plugin {

    private static QuietNativePlugin instance;
    private volatile boolean volumeKeysEnabled = false;

    @Override
    public void load() {
        instance = this;
    }

    // ---------- Incoming files ----------

    @Override
    protected void handleOnNewIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            uris.add(intent.getData());
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = getParcelableUri(intent);
            if (uri != null) uris.add(uri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            List<Uri> list = getParcelableUris(intent);
            if (list != null) uris.addAll(list);
        }
        if (uris.isEmpty()) return;
        // Handled once: don't import the same file again if the activity is recreated.
        intent.setAction(null);
        new Thread(() -> {
            for (Uri uri : uris) copyAndAnnounce(uri);
        }).start();
    }

    @SuppressWarnings("deprecation")
    private Uri getParcelableUri(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    @SuppressWarnings("deprecation")
    private List<Uri> getParcelableUris(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
        return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    }

    private void copyAndAnnounce(Uri uri) {
        ContentResolver resolver = getContext().getContentResolver();
        String name = displayName(resolver, uri);
        String type = resolver.getType(uri);
        File dir = new File(getContext().getCacheDir(), "incoming");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, System.currentTimeMillis() + "-" + name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        try (InputStream in = resolver.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new Exception("No data");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) os.write(buffer, 0, n);
        } catch (Exception e) {
            JSObject error = new JSObject();
            error.put("name", name);
            error.put("error", String.valueOf(e.getMessage()));
            notifyListeners("incomingFile", error, true);
            return;
        }
        JSObject data = new JSObject();
        data.put("path", out.getAbsolutePath());
        data.put("name", name);
        data.put("type", type == null ? "" : type);
        // Kept until the web app starts listening, in case Quiet was just launched for this file.
        notifyListeners("incomingFile", data, true);
    }

    private String displayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception ignored) {}
        if (name == null) name = uri.getLastPathSegment();
        return name == null || name.isEmpty() ? "book" : name;
    }

    // Deletes a copied file once the web app has added it to the library.
    @PluginMethod
    public void release(PluginCall call) {
        String path = call.getString("path");
        File dir = new File(getContext().getCacheDir(), "incoming");
        if (path != null) {
            File file = new File(path);
            if (dir.equals(file.getParentFile())) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        call.resolve();
    }

    // ---------- Volume keys ----------

    @PluginMethod
    public void setVolumeKeys(PluginCall call) {
        volumeKeysEnabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
        call.resolve();
    }

    /** Called by MainActivity for every key. Returns true if the key was used to turn a page. */
    static boolean handleKey(KeyEvent event) {
        QuietNativePlugin plugin = instance;
        if (plugin == null || !plugin.volumeKeysEnabled) return false;
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_UP) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            JSObject data = new JSObject();
            data.put("direction", code == KeyEvent.KEYCODE_VOLUME_DOWN ? "next" : "prev");
            plugin.notifyListeners("volumeKey", data);
        }
        return true;
    }

    // ---------- Keep the screen on ----------

    @PluginMethod
    public void keepAwake(PluginCall call) {
        boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
        getActivity().runOnUiThread(() -> {
            if (enabled) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        call.resolve();
    }

    // ---------- Screen rotation ----------

    @PluginMethod
    public void setOrientation(PluginCall call) {
        String value = call.getString("value", "auto");
        int orientation = "portrait".equals(value) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            : "landscape".equals(value) ? ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        getActivity().runOnUiThread(() -> getActivity().setRequestedOrientation(orientation));
        call.resolve();
    }

    // ---------- Haptics ----------

    @PluginMethod
    public void haptic(PluginCall call) {
        String kind = call.getString("kind", "tick");
        int feedback = "confirm".equals(kind) && Build.VERSION.SDK_INT >= 30
            ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.CLOCK_TICK;
        getActivity().runOnUiThread(() -> getBridge().getWebView().performHapticFeedback(feedback));
        call.resolve();
    }

    // ---------- Battery ----------

    @PluginMethod
    public void battery(PluginCall call) {
        Intent status = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        JSObject data = new JSObject();
        if (status == null) {
            call.reject("Battery level unavailable");
            return;
        }
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        data.put("level", level < 0 || scale <= 0 ? 1.0 : level / (double) scale);
        data.put("charging", plugged != 0);
        call.resolve(data);
    }
}
