package com.jabstone.jabtalk.basic.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AboutActivity extends Activity {

    private static final String RELEASES_API =
            "https://api.github.com/repos/cwi-vvatsos/JABtalk/releases/latest";
    private static final String RELEASES_PAGE =
            "https://github.com/cwi-vvatsos/JABtalk/releases";
    private static final String TAG = AboutActivity.class.getSimpleName();

    private TextView statusView;
    private Button checkButton;
    private CheckUpdatesTask task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_activity);
        setTitle(R.string.menu_about);

        TextView version = findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version_fmt, JTApp.getVersionName()));

        statusView = findViewById(R.id.about_update_status);
        checkButton = findViewById(R.id.about_check_updates);
        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) return;
                statusView.setText(R.string.about_checking);
                checkButton.setEnabled(false);
                task = new CheckUpdatesTask();
                task.execute();
            }
        });

        Button supportButton = findViewById(R.id.about_support);
        supportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(JTApp.URL_SUPPORT)));
                } catch (Exception e) {
                    JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                            "Could not open support URL: " + e.getMessage());
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (task != null) task.cancel(true);
    }

    private void openReleases() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)));
        } catch (Exception e) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Could not open releases: " + e.getMessage());
        }
    }

    private static int compareVersions(String a, String b) {
        // Strip leading v/V and any non-numeric suffix; compare component-wise.
        String[] pa = normalize(a).split("\\.");
        String[] pb = normalize(b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int ai = i < pa.length ? parse(pa[i]) : 0;
            int bi = i < pb.length ? parse(pb[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static String normalize(String v) {
        if (v == null) return "0";
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        return s;
    }

    private static int parse(String s) {
        try {
            StringBuilder num = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') num.append(c);
                else break;
            }
            return num.length() == 0 ? 0 : Integer.parseInt(num.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private class CheckUpdatesTask extends AsyncTask<Void, Void, String> {
        private String errorMsg = null;

        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(RELEASES_API);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    errorMsg = "HTTP " + code;
                    return null;
                }
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                JSONObject json = new JSONObject(sb.toString());
                return json.optString("tag_name", null);
            } catch (Exception e) {
                errorMsg = e.getMessage();
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String remoteTag) {
            checkButton.setEnabled(true);
            if (isCancelled()) return;
            if (remoteTag == null || remoteTag.isEmpty()) {
                statusView.setText(R.string.about_check_failed);
                JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_WARNING,
                        "Update check failed: " + errorMsg);
                return;
            }
            String local = JTApp.getVersionName();
            if (compareVersions(remoteTag, local) > 0) {
                statusView.setText(getString(R.string.about_update_available, remoteTag));
                checkButton.setText(R.string.about_open_releases);
                checkButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openReleases();
                    }
                });
            } else {
                statusView.setText(R.string.about_up_to_date);
            }
        }
    }
}
