package example.naoki.ble_myo.uihelper;

import android.graphics.Color;
import android.widget.TextView;

import java.util.Locale;

/**
 * Created by timestring on 3/31/17.
 */
public class TextViewCountingHelper {
    private static final int WARM_UP_REPS = 2;

    private TextView mTextView;
    private long repDurationMs;
    private long startTimeMs;
    private boolean isCounting = false;

    public TextViewCountingHelper(TextView textView, long defaultRepDurationMs) {
        mTextView = textView;
        repDurationMs = defaultRepDurationMs;
    }

    public void start(double repDurationSec) {
        startTimeMs = System.currentTimeMillis();
        repDurationMs = (long)(repDurationSec * 1000);
        isCounting = true;
    }

    public void stop() {
        isCounting = false;
    }

    public void draw() {
        if (!isCounting)
            return;

        long nowMs = System.currentTimeMillis();
        int cycle = (int)((nowMs - startTimeMs) / repDurationMs);
        long offset = (nowMs - startTimeMs) % repDurationMs;
        double remainRepSec = (double)(repDurationMs - offset) / 1000.;
        int intensityDeductionRatio = 1;
        String displayStr;

        if (cycle < WARM_UP_REPS) {
            long startCollectionMs = startTimeMs + WARM_UP_REPS * repDurationMs;
            int remainSec = (int)((startCollectionMs - nowMs) / 1000) + 1;
            displayStr = String.format(Locale.getDefault(), "Count down %d (%.2f)", remainSec, remainRepSec);
            intensityDeductionRatio = 2;
        }
        else {
            int repIdx = cycle - WARM_UP_REPS + 1;
            displayStr = String.format(Locale.getDefault(), "Rep %d (%.2f)", repIdx, remainRepSec);
        }
        mTextView.setText(displayStr);

        float intensityRatio = Math.max(1.f - (float)offset / 300.f, 0.f);
        int intensity = (int)(255 * intensityRatio) / intensityDeductionRatio;
        mTextView.setBackgroundColor(Color.rgb(255, 255 - intensity, 255 - intensity));
        if (offset < 150L)
            mTextView.setTextColor(Color.WHITE);
        else
            mTextView.setTextColor(Color.RED);
    }
}
