package io.whisper.webbrowser;

import android.content.Context;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;

/**
 * Created by suleyu on 2017/6/30.
 */

public class CustomImageButton extends AppCompatImageButton {

    public CustomImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if(this.isEnabled() != enabled) {
            this.setImageAlpha(enabled ? 0xFF : 0x3F);
        }
        super.setEnabled(enabled);
    }
}
