package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * v2.0.4 (owner's 16 Nov directive "universal deep linking from WhatsApp, Chrome and any browser → direct restore"): a
 * link to a Sketchware AI project repository ({@code https://github.com/<owner>/SketchwareAI-<name>…}) or
 * {@code sketchwareai://import?url=<github link>} opens Restore projects with that link, which looks at it at once and
 * offers its versions to import (a private repository of another account says so). Nothing is restored without a tap.
 * Android 12+ hands github.com links to an app only when the user added them under the app's "Open by default"
 * settings (github.com isn't this app's domain to verify); the restore screen has a shortcut there.
 */
public class GitHubLinkActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        open(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        open(intent);
    }

    private void open(Intent intent) {
        String link = linkOf(intent == null ? null : intent.getData());
        GitHubImport.Source source = link == null ? null : GitHubImport.parse(link);
        if (source != null && !source.file && (source.path == null || source.path.isEmpty())) {
            // v2.0.5: a repository link opens the repository in the app (details, README, versions to restore)
            startActivity(RepoViewerActivity.intent(this, source.owner, source.repo));
            finish();
            return;
        }
        Intent next = new Intent(this, GitHubBackupsActivity.class);
        if (link != null) next.putExtra(GitHubBackupsActivity.EXTRA_LINK, link);
        // v2.1.2 review (MEDIUM): never CLEAR_TOP: the GitHub tab may be kept behind Home, and clearing above it would
        // close Home (Back then left the app)
        next.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(next);
        finish();
    }

    /** The GitHub link a VIEW intent carries (https, or sketchwareai://import?url=…), or null. */
    static String linkOf(Uri data) {
        if (data == null) return null;
        String scheme = data.getScheme();
        if ("sketchwareai".equals(scheme)) {
            String url = data.getQueryParameter("url");
            if (url == null) {
                // sketchwareai://import/<owner>/<repo>
                String path = data.getPath();
                if (path != null && path.matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?")) url = "https://github.com" + path;
            }
            return url != null && GitHubImport.parse(url) != null ? url : null;
        }
        if ("https".equals(scheme) && data.getHost() != null
                && (data.getHost().equalsIgnoreCase("github.com") || data.getHost().equalsIgnoreCase("www.github.com"))) {
            return data.toString();
        }
        return null;
    }
}
