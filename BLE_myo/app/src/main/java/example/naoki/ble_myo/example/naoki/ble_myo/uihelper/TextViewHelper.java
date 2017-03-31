package example.naoki.ble_myo.example.naoki.ble_myo.uihelper;

import android.widget.TextView;

/**
 * Created by timestring on 3/31/17.
 */
public class TextViewHelper {
    private TextView mTextView;
    private String mString;

    public TextViewHelper(TextView textView) {
        mTextView = textView;
    }

    public void update(String str) {
        mString = str;
    }

    public void draw() {
        mTextView.setText(mString);
    }
}
