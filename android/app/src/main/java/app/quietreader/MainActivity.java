package app.quietreader;

import android.os.Bundle;
import android.view.KeyEvent;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(QuietNativePlugin.class);
        super.onCreate(savedInstanceState);
    }

    // Volume keys turn pages while reading, if that's switched on in Quiet's settings.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (QuietNativePlugin.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
