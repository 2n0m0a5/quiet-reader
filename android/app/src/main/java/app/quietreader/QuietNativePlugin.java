package app.quietreader;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.os.Environment;
import android.util.Base64;
import androidx.core.content.FileProvider;
import android.provider.OpenableColumns;
import android.view.KeyEvent;
import android.view.WindowManager;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
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
 *  - Book folders: the reader picks folders full of books; Quiet keeps permission to read them,
 *    lists the books inside (sub-folders too) and copies new ones in for the web app to add.
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

    // Copies a file the web app can't read directly into the app's cache, where it can.
    private File copyToCache(Uri uri, String name) throws Exception {
        File dir = new File(getContext().getCacheDir(), "incoming");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, System.currentTimeMillis() + "-" + name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        try (InputStream in = getContext().getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new Exception("No data");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) os.write(buffer, 0, n);
        }
        return out;
    }

    private void copyAndAnnounce(Uri uri) {
        ContentResolver resolver = getContext().getContentResolver();
        String name = displayName(resolver, uri);
        String type = resolver.getType(uri);
        File out;
        try {
            out = copyToCache(uri, name);
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

    // Uses the vibration motor directly: the WebView's own haptic feedback is skipped by some
    // phones (Xiaomi's HyperOS among them). Quiet's Vibration setting turns this off.
    @PluginMethod
    @SuppressWarnings("deprecation")
    public void haptic(PluginCall call) {
        boolean confirm = "confirm".equals(call.getString("kind", "tick"));
        Vibrator vibrator = Build.VERSION.SDK_INT >= 31
            ? ((VibratorManager) getContext().getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator()
            : (Vibrator) getContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            call.resolve();
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(confirm ? VibrationEffect.EFFECT_HEAVY_CLICK : VibrationEffect.EFFECT_CLICK));
        } else if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(confirm ? 20 : 10, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(confirm ? 20 : 10);
        }
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

    // ---------- Book folders ----------

    // The same list as src/formats/formats.ts.
    private static final String[] BOOK_EXTENSIONS = {
        "epub", "mobi", "azw3", "azw", "prc", "pdb", "fb2", "fbz", "pdf", "docx", "odt", "rtf", "md", "markdown",
        "txt", "txtz", "html", "htm", "xhtml", "htmlz", "cbz", "cbr", "cb7", "cbt", "zip", "rar", "7z", "tar" };
    private static final int MAX_DEPTH = 8;
    private static final int MAX_FILES = 5000;

    // Opens Android's folder picker. Resolves with the folder, or { cancelled: true }.
    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "folderPicked");
    }

    @ActivityCallback
    private void folderPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            JSObject cancelled = new JSObject();
            cancelled.put("cancelled", true);
            call.resolve(cancelled);
            return;
        }
        Uri tree = data.getData();
        // Keep the permission after a restart, so Quiet can check the folder for new books.
        try {
            getContext().getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}
        JSObject folder = new JSObject();
        folder.put("uri", tree.toString());
        folder.put("name", folderName(tree));
        call.resolve(folder);
    }

    // Lists the books in a folder and the folders inside it: { name, files: [{ uri, name, size, modified }] }.
    @PluginMethod
    public void scanFolder(PluginCall call) {
        String value = call.getString("uri");
        if (value == null) {
            call.reject("No folder");
            return;
        }
        new Thread(() -> {
            try {
                Uri tree = Uri.parse(value);
                JSArray files = new JSArray();
                walk(tree, DocumentsContract.getTreeDocumentId(tree), files, 0);
                JSObject result = new JSObject();
                result.put("name", folderName(tree));
                result.put("files", files);
                call.resolve(result);
            } catch (Exception e) {
                call.reject("Couldn't read the folder: " + e.getMessage());
            }
        }).start();
    }

    private void walk(Uri tree, String parentId, JSArray files, int depth) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] columns = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        };
        try (Cursor cursor = getContext().getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext() && files.length() < MAX_FILES) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (name == null || name.startsWith(".")) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (depth < MAX_DEPTH) walk(tree, id, files, depth + 1);
                    continue;
                }
                if (!isBook(name)) continue;
                JSObject file = new JSObject();
                file.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                file.put("name", name);
                file.put("size", cursor.isNull(3) ? 0 : cursor.getLong(3));
                file.put("modified", cursor.isNull(4) ? 0 : cursor.getLong(4));
                files.put(file);
            }
        }
    }

    private static boolean isBook(String name) {
        String lower = name.toLowerCase();
        for (String ext : BOOK_EXTENSIONS) if (lower.endsWith("." + ext)) return true;
        return false;
    }

    private String folderName(Uri tree) {
        try {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            try (Cursor cursor = getContext().getContentResolver().query(doc, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) return cursor.getString(0);
            }
        } catch (Exception ignored) {}
        String last = tree.getLastPathSegment();
        return last == null ? "Folder" : last.substring(last.lastIndexOf(':') + 1);
    }

    // Copies one book from a folder into the cache: { path, name, type }. Release it afterwards.
    @PluginMethod
    public void copyFile(PluginCall call) {
        String value = call.getString("uri");
        String name = call.getString("name", "book");
        if (value == null) {
            call.reject("No file");
            return;
        }
        new Thread(() -> {
            try {
                Uri uri = Uri.parse(value);
                File out = copyToCache(uri, name);
                String type = getContext().getContentResolver().getType(uri);
                JSObject result = new JSObject();
                result.put("path", out.getAbsolutePath());
                result.put("name", name);
                result.put("type", type == null ? "" : type);
                call.resolve(result);
            } catch (Exception e) {
                call.reject("Couldn't copy " + name + ": " + e.getMessage());
            }
        }).start();
    }

    // The reader removed the folder from Quiet: give back the permission to read it.
    @PluginMethod
    public void forgetFolder(PluginCall call) {
        String value = call.getString("uri");
        try {
            if (value != null) getContext().getContentResolver().releasePersistableUriPermission(Uri.parse(value), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        call.resolve();
    }

    // ---------- Pictures of quotes ----------

    // Shares a PNG (given as base64) through Android's share sheet.
    @PluginMethod
    public void shareImage(PluginCall call) {
        try {
            byte[] bytes = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
            File dir = new File(getContext().getCacheDir(), "shared");
            dir.mkdirs();
            File file = new File(dir, call.getString("name", "quote.png"));
            try (OutputStream out = new FileOutputStream(file)) { out.write(bytes); }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            String text = call.getString("text");
            if (text != null) send.putExtra(Intent.EXTRA_TEXT, text);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, null);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject("Couldn't share the picture", e);
        }
    }

    // Saves a PNG (given as base64) to Pictures/Quiet, where the gallery shows it.
    @PluginMethod
    public void saveImage(PluginCall call) {
        try {
            byte[] bytes = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
            String name = call.getString("name", "quote.png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Quiet");
                Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("No place to save");
                try (OutputStream out = getContext().getContentResolver().openOutputStream(uri)) { out.write(bytes); }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Quiet");
                dir.mkdirs();
                try (OutputStream out = new FileOutputStream(new File(dir, name))) { out.write(bytes); }
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Couldn't save the picture", e);
        }
    }
}
