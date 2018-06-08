package org.fdroid.fdroid.views;

import android.content.Context;
import android.support.v7.preference.PreferenceViewHolder;
import android.support.v7.preference.SeekBarPreference;
import android.util.AttributeSet;
import android.widget.SeekBar;
import org.fdroid.fdroid.R;

public class LiveSeekBarPreference extends SeekBarPreference {
    private Runnable progressChangedRunnable;
    private boolean trackingTouch;
    private int value = -1;

    @SuppressWarnings("unused")
    public LiveSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @SuppressWarnings("unused")
    public LiveSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressWarnings("unused")
    public LiveSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressWarnings("unused")
    public LiveSeekBarPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        SeekBar seekbar = holder.itemView.findViewById(R.id.seekbar);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = progress;
                if (progressChangedRunnable != null) {
                    progressChangedRunnable.run();
                }
                if (fromUser && !trackingTouch) {
                    persistInt(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                trackingTouch = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                trackingTouch = false;
                persistInt(value);
            }
        });
        seekbar.setProgress(value);
    }

    @Override
    public void setValue(int value) {
        super.setValue(value);
        this.value = value;
    }

    @Override
    public int getValue() {
        if (value == -1) {
            value = super.getValue();
        }
        return value;
    }

    public void setProgressChangedRunnable(Runnable runnable) {
        progressChangedRunnable = runnable;
    }
}
