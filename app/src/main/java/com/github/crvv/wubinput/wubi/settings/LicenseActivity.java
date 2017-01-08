package com.github.crvv.wubinput.wubi.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;
import com.github.crvv.wubinput.wubi.R;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class LicenseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        ActionBar actionBar = getActionBar();
        if(actionBar != null)actionBar.setDisplayHomeAsUpEnabled(true);
        TextView testText = (TextView)findViewById(R.id.test_text1);

        InputStream is = getResources().openRawResource(R.raw.notice);
        try {
            String notice = IOUtils.toString(is, StandardCharsets.UTF_8);
            testText.setText(notice);
        } catch (IOException e) {
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }
}
