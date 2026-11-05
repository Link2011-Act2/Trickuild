package dev.trickuild.externalconsumer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText(MetadataMessage.render() + " / " + BuildConfig.VERSION_NAME);
        setContentView(text);
    }
}
