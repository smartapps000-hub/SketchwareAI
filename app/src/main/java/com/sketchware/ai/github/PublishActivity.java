package com.sketchware.ai.github;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.gson.JsonObject;
import com.sketchware.ai.project.ProjectDiffAnalyzer;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.remod.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.yB;

/**
 * v2.0.4 (owner's 16 Nov directive "tapping Private opens a full-screen Publish Project to GitHub glass sheet, never
 * github.com"): everything the project's public page says, pre-filled by the on-device analyzer
 * ({@link ProjectDiffAnalyzer}) and editable: title, description (GitHub's About), topics as chips, README.md (on / off,
 * with its text), and the newest version's release notes ("What's New & Fixed"). "Publish Public" hands it to
 * {@link CloudSync#publish}: the first version is backed up when there is none, the page is written, the repository
 * made public. Opened again from the Public pill ("Edit public details"), the same screen saves and updates.
 */
public class PublishActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_EDIT = "edit";

    private GitHubViews v;
    private LinearLayout list;
    private String scId;
    private String name;
    private boolean edit;
    private PublishDetails details;
    /** What the analyzer wrote (to tell the user's edits from it). */
    private String generatedTitle;
    private String generatedDescription;
    private String generatedReadme;
    private List<String> generatedTopics;

    private EditText title;
    private EditText description;
    private EditText readme;
    private EditText notes;
    private android.widget.CheckBox readmeOn;
    private FlowLayout chips;
    private final List<String> topics = new ArrayList<>();

    public static void open(android.app.Activity activity, String scId, boolean edit) {
        activity.startActivity(new Intent(activity, PublishActivity.class).putExtra(EXTRA_SC_ID, scId)
                .putExtra(EXTRA_EDIT, edit));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github_screen);
        ObsidianWindow.apply(this);
        v = new GitHubViews(this);
        list = findViewById(R.id.gs_list);
        findViewById(R.id.gs_back).setOnClickListener(x -> finish());
        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        edit = getIntent().getBooleanExtra(EXTRA_EDIT, false);
        HashMap<String, Object> project = scId == null ? null : lC.b(scId);
        if (project == null) {
            finish();
            return;
        }
        name = yB.c(project, "my_ws_name");
        ((TextView) findViewById(R.id.gs_title)).setText(edit ? "Public details" : "Publish to GitHub");
        String login = TokenStore.get(this).login();
        String repo = ProjectLinks.repoName(scId, login);
        ((TextView) findViewById(R.id.gs_subtitle)).setText(repo != null ? "github.com/" + login + "/" + repo
                : "github.com/" + login + "/" + ProjectRepo.repositoryName(name) + " (made on publish)");
        list.addView(v.line("Reading the project…", false));
        // the analyzer reads the saved files: off the main thread
        Thread t = new Thread(() -> {
            PublishDetails d = PublishDetails.load(scId);
            JsonObject snapshot = ProjectDiffAnalyzer.snapshot(scId);
            ProjectDiffAnalyzer.Changes changes = ProjectDiffAnalyzer.compare(ProjectDiffAnalyzer.previous(scId, snapshot),
                    snapshot);
            String tag = ProjectRepo.tagFor(ProjectDiffAnalyzer.string(snapshot, "versionName"),
                    ProjectDiffAnalyzer.string(snapshot, "versionCode"));
            String link = login == null ? null : ProjectRepo.link(login, repo != null ? repo : ProjectRepo.repositoryName(name));
            String newNotes = tag.equals(d.notesTag) && d.notes != null ? null : ProjectDiffAnalyzer.releaseNotes(snapshot, changes);
            d.refresh(snapshot, tag, newNotes, null, link);
            String gTitle = ProjectDiffAnalyzer.title(snapshot);
            String gDescription = ProjectDiffAnalyzer.description(snapshot);
            List<String> gTopics = ProjectDiffAnalyzer.topics(snapshot);
            String gReadme = ProjectDiffAnalyzer.readme(snapshot, d.title, d.description, link);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                details = d;
                generatedTitle = gTitle;
                generatedDescription = gDescription;
                generatedTopics = gTopics;
                generatedReadme = gReadme;
                build();
            });
        }, "PublishDetails");
        t.setDaemon(true);
        t.start();
    }

    private void build() {
        list.removeAllViews();
        list.addView(v.line(edit ? "Change what the project's public page says; later versions keep it up to date by "
                + "themselves."
                : "Everything is ready from the project itself; change anything you like. After publishing, every new "
                + "version updates the page by itself (README, CHANGELOG, release notes).", false), v.full(-1, 4));

        list.addView(label("Title"), v.full(-1, 18));
        title = field(details.title, false, 1);
        list.addView(title, v.full(48, 6));

        list.addView(label("Description (GitHub's About, up to 350 characters)"), v.full(-1, 16));
        description = field(details.description, true, 3);
        list.addView(description, v.full(-1, 6));

        list.addView(label("Topics"), v.full(-1, 16));
        chips = new FlowLayout(this, v.dp(8));
        topics.clear();
        topics.addAll(details.topics);
        list.addView(chips, v.full(-1, 6));
        LinearLayout add = new LinearLayout(this);
        add.setOrientation(LinearLayout.HORIZONTAL);
        add.setGravity(Gravity.CENTER_VERTICAL);
        EditText topic = field("", false, 1);
        topic.setHint("Add a topic (e.g. tools)");
        add.addView(topic, new LinearLayout.LayoutParams(0, v.dp(44), 1f));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, v.dp(40));
        ap.leftMargin = v.dp(8);
        add.addView(v.smallPill("Add", false, true, x -> {
            String t = ProjectDiffAnalyzer.topic(topic.getText().toString());
            if (t != null && !topics.contains(t) && topics.size() < 20) topics.add(t);
            topic.setText("");
            renderChips();
        }), ap);
        list.addView(add, v.full(-1, 8));
        renderChips();

        readmeOn = v.checkBox("Include README.md", details.readmeOn);
        list.addView(readmeOn, v.full(-1, 14));
        readme = field(details.readme, true, 12);
        readme.setTypeface(android.graphics.Typeface.MONOSPACE);
        readme.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        readme.setVisibility(details.readmeOn ? View.VISIBLE : View.GONE);
        readmeOn.setOnCheckedChangeListener((b, on) -> readme.setVisibility(on ? View.VISIBLE : View.GONE));
        list.addView(readme, v.full(-1, 6));

        list.addView(label("What's New & Fixed (the newest version's release notes)"), v.full(-1, 16));
        notes = field(details.notes, true, 8);
        notes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        list.addView(notes, v.full(-1, 6));

        list.addView(v.pill(edit ? "Save & update" : "Publish Public", false, true, x -> publish()), v.full(50, 22));
        TextView note = v.line(edit ? "The project stays public." : "Publishing makes the repository public: anyone "
                + "can download the project and its versions, including keys or passwords written in it.", false);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(note, v.full(-1, 8));
    }

    private TextView label(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Label);
        t.setText(text);
        t.setTypeface(v.bold());
        return t;
    }

    private EditText field(String text, boolean multi, int lines) {
        // v2.0.5: a field that takes typing (GitHubViews.input)
        EditText e = v.input(null, multi, lines);
        e.setText(text == null ? "" : text);
        return e;
    }

    private void renderChips() {
        chips.removeAllViews();
        if (!topics.contains(ProjectRepo.TOPIC)) topics.add(0, ProjectRepo.TOPIC);
        for (String t : new ArrayList<>(topics)) {
            boolean kept = ProjectRepo.TOPIC.equals(t);
            TextView chip = v.smallPill(kept ? "#" + t : "#" + t + "  ✕", false, true, x -> {
                if (kept) {
                    Toast.makeText(this, "#" + ProjectRepo.TOPIC + " stays: your other phones find this project's "
                            + "repository by it", Toast.LENGTH_LONG).show();
                    return;
                }
                topics.remove(t);
                renderChips();
            });
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            GitHubViews.oneLine(chip);
            chip.setContentDescription(kept ? "Topic " + t + ", kept" : "Remove topic " + t);
            chips.addView(chip, new ViewGroup.LayoutParams(-2, v.dp(34)));
        }
    }

    private void publish() {
        if (TokenStore.get(this).login() == null) {
            GitHubInAppAuthDialog.show(this, login -> {
                if (!isFinishing()) publish();
            });
            return;
        }
        if (!AuthGuard.check(this)) return;
        details.title = title.getText().toString().trim();
        details.titleCustom = !details.title.equals(generatedTitle);
        details.description = description.getText().toString().replace('\n', ' ').trim();
        details.descriptionCustom = !details.description.equals(generatedDescription);
        details.topics.clear();
        details.topics.addAll(topics);
        details.topicsCustom = !topics.equals(generatedTopics);
        details.readmeOn = readmeOn.isChecked();
        details.readme = readme.getText().toString();
        details.readmeCustom = !details.readme.equals(generatedReadme);
        details.notes = notes.getText().toString();
        // v2.0.5: Public details are saved at once on their own (a first Publish runs as a job: it may back up first)
        GitHubProgress progress = edit && "public".equals(ProjectLinks.visibility(scId, TokenStore.get(this).login()))
                ? CloudSync.saveDetails(this, scId, name, details) : CloudSync.publish(this, scId, name, details);
        progress.whenClosed(ok -> {
            if (ok && !isFinishing()) finish();
        });
    }

    /** Lays its children out in rows, wrapping to the next row (the topic chips). */
    static final class FlowLayout extends ViewGroup {
        private final int gap;

        FlowLayout(android.content.Context c, int gap) {
            super(c);
            this.gap = gap;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int x = 0;
            int y = 0;
            int row = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                LayoutParams lp = child.getLayoutParams();
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        lp.height > 0 ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                                : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = child.getMeasuredWidth();
                if (x > 0 && x + w > width) {
                    x = 0;
                    y += row + gap;
                    row = 0;
                }
                x += w + gap;
                row = Math.max(row, child.getMeasuredHeight());
            }
            setMeasuredDimension(width, y + row);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int x = 0;
            int y = 0;
            int row = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                int w = child.getMeasuredWidth();
                int h = child.getMeasuredHeight();
                if (x > 0 && x + w > width) {
                    x = 0;
                    y += row + gap;
                    row = 0;
                }
                child.layout(x, y, x + w, y + h);
                x += w + gap;
                row = Math.max(row, h);
            }
        }
    }
}
