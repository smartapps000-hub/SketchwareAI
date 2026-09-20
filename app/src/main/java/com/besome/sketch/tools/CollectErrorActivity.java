package com.besome.sketch.tools;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.sketchware.ai.diagnostic.CrashDiagnostics;
import com.sketchware.remod.R;

/**
 * Crash report shown after an uncaught exception (see SketchApplication): an Obsidian glass
 * sheet with the root cause explained offline, the full stack trace on demand, and
 * Show error / Copy / Close / Share report.
 * <p>
 * The old "Send" posted to Sketchware Pro's crash webhook, which this build doesn't have (the
 * URL comes from a build-time variable that is empty here), so it always failed. "Share
 * report" hands the same report to any app instead (chat, mail, notes).
 * <p>
 * Only framework views, plain shape drawables and system fonts are used here, so this screen
 * keeps working whatever caused the crash.
 */
public class CollectErrorActivity extends Activity {

    private String report = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_crash_report);
        com.sketchware.ai.ui.ObsidianWindow.apply(this);

        Intent intent = getIntent();
        String error = intent == null ? null : intent.getStringExtra("error");
        if (error == null) error = "";

        CrashDiagnostics.Result result = CrashDiagnostics.explain(error);
        String device = deviceInfo();
        report = "Sketchware AI crash report\n" + device + "\n\n" + error;

        ((TextView) findViewById(R.id.ob_crash_subtitle)).setText(appVersion() + " · Android "
                + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        ((TextView) findViewById(R.id.ob_crash_cause)).setText(
                result.where == null ? result.cause : result.cause + "\nat " + result.where);
        ((TextView) findViewById(R.id.ob_crash_advice)).setText(result.advice);
        ((TextView) findViewById(R.id.ob_crash_log)).setText(error.isEmpty() ? "No stack trace was saved." : error);

        View logPanel = findViewById(R.id.ob_crash_log_panel);
        TextView show = findViewById(R.id.ob_crash_show);
        show.setOnClickListener(v -> {
            boolean open = logPanel.getVisibility() != View.VISIBLE;
            logPanel.setVisibility(open ? View.VISIBLE : View.GONE);
            show.setText(open ? "Hide error" : "Show error");
        });
        findViewById(R.id.ob_crash_copy).setOnClickListener(v -> copyReport());
        findViewById(R.id.ob_crash_close).setOnClickListener(v -> finish());
        findViewById(R.id.ob_crash_share).setOnClickListener(v -> shareReport());
    }

    private void copyReport() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Sketchware AI crash report", report));
            Toast.makeText(this, "Crash report copied (" + report.length() + " characters)", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Couldn't copy: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareReport() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Sketchware AI crash report");
        send.putExtra(Intent.EXTRA_TEXT, report);
        try {
            startActivity(Intent.createChooser(send, "Share crash report"));
        } catch (RuntimeException e) {
            copyReport();
        }
    }

    private String appVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "Sketchware AI " + info.versionName;
        } catch (Exception e) {
            return "Sketchware AI";
        }
    }

    private String deviceInfo() {
        long versionCode = 0;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) {
        }
        return appVersion() + " (" + versionCode + ")\n"
                + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.BRAND + ")\n"
                + "ABI: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
    }
}
