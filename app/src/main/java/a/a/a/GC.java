package a.a.a;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.besome.sketch.MainActivity;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.manage.library.ProjectComparator;
import com.besome.sketch.export.ExportProjectActivity;
import com.besome.sketch.lib.ui.CircleImageView;
import com.besome.sketch.projects.MyProjectButton;
import com.besome.sketch.projects.MyProjectButtonLayout;
import com.besome.sketch.projects.MyProjectSettingActivity;
import com.sketchware.remod.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import mod.hey.studios.project.ProjectSettingsDialog;
import mod.hey.studios.project.ProjectTracker;
import mod.hey.studios.project.backup.BackupRestoreManager;

public class GC extends DA implements View.OnClickListener {

    private static final int REQUEST_CODE_DESIGN_ACTIVITY = 204;
    private static final int REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY = 206;
    private static final int REQUEST_CODE_RESTORE_PROJECT = 700;

    private SwipeRefreshLayout swipeRefresh;
    private ArrayList<HashMap<String, Object>> projectsList = new ArrayList<>();
    private RecyclerView myProjects;
    private View cvCreateNew;
    private View cvRestoreProjects;
    /** v2.0.5 review (HIGH): found in initialize (getView() is still null during the first load in onCreateView). */
    private View demoSection;
    private LinearLayout demoRow;
    /** Every project on disk; {@link #projectsList} is what the search filter lets through. */
    private ArrayList<HashMap<String, Object>> allProjects = new ArrayList<>();
    private String searchQuery = "";
    private Boolean isCollapsed;
    private AnimatorSet collapseAnimatorSet;
    private AnimatorSet expandAnimatorSet;
    private ProjectsAdapter projectsAdapter;

    private void toProjectSettingOrRequestPermission(int position) {
        if (super.c()) {
            Intent intent = new Intent(getContext(), MyProjectSettingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("sc_id", yB.c(projectsList.get(position), "sc_id"));
            intent.putExtra("is_update", true);
            intent.putExtra("advanced_open", false);
            intent.putExtra("index", position);
            startActivityForResult(intent, REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY);
        } else if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).s();
        }
    }

    private void initialize(ViewGroup parent) {
        swipeRefresh = parent.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            if (swipeRefresh.d()) swipeRefresh.setRefreshing(false);

            if (c()) {
                refreshProjectsList();
            } else if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).s();
            }
        });
        // v2.1.0: no floating + button (the bottom bar's Create tab makes projects)

        myProjects = parent.findViewById(R.id.myprojects);
        myProjects.setHasFixedSize(true);
        myProjects.setLayoutManager(new LinearLayoutManager(getContext()));
        projectsAdapter = new ProjectsAdapter(myProjects);
        myProjects.setAdapter(projectsAdapter);
        myProjects.setItemAnimator(new ci());

        cvCreateNew = parent.findViewById(R.id.cv_create_new);
        cvCreateNew.setOnClickListener(this);
        demoSection = parent.findViewById(R.id.ob_demo_section);
        demoRow = parent.findViewById(R.id.ob_demo_row);

        isCollapsed = false;

        cvRestoreProjects = parent.findViewById(R.id.cv_restore_projects);
        cvRestoreProjects.setOnClickListener(this);
        ImageView ivRestoreProjects = parent.findViewById(R.id.iv_restore_projects);
        TextView tvRestoreSubtitle = parent.findViewById(R.id.tv_restore_subtitle);

        // Scrolling the list folds the 72dp restore card to a 56dp title-only bar and back.
        collapseAnimatorSet = new AnimatorSet();
        expandAnimatorSet = new AnimatorSet();
        ValueAnimator collapseValueAnimator = ValueAnimator.ofFloat(wB.a(getContext(), 72.0F), wB.a(getContext(), 56.0F));
        collapseValueAnimator.addUpdateListener(valueAnimator -> {
            float value = (Float) valueAnimator.getAnimatedValue();
            cvRestoreProjects.getLayoutParams().height = (int) value;
            cvRestoreProjects.requestLayout();
        });
        ValueAnimator expandValueAnimator = ValueAnimator.ofFloat(wB.a(getContext(), 56.0F), wB.a(getContext(), 72.0F));
        expandValueAnimator.addUpdateListener(valueAnimator -> {
            float value = (Float) valueAnimator.getAnimatedValue();
            cvRestoreProjects.getLayoutParams().height = (int) value;
            cvRestoreProjects.requestLayout();
        });
        ValueAnimator subtitleOut = ValueAnimator.ofFloat(1.0F, 0.0F);
        subtitleOut.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            tvRestoreSubtitle.setAlpha(f);
            tvRestoreSubtitle.setVisibility(f <= 0.01F ? View.GONE : View.VISIBLE);
        });
        ValueAnimator subtitleIn = ValueAnimator.ofFloat(0.0F, 1.0F);
        subtitleIn.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            tvRestoreSubtitle.setVisibility(View.VISIBLE);
            tvRestoreSubtitle.setAlpha(f);
        });
        collapseAnimatorSet.playTogether(collapseValueAnimator, subtitleOut,
                ObjectAnimator.ofFloat(ivRestoreProjects, View.SCALE_X, 1.0F, 0.82F),
                ObjectAnimator.ofFloat(ivRestoreProjects, View.SCALE_Y, 1.0F, 0.82F));
        expandAnimatorSet.playTogether(expandValueAnimator, subtitleIn,
                ObjectAnimator.ofFloat(ivRestoreProjects, View.SCALE_X, 0.82F, 1.0F),
                ObjectAnimator.ofFloat(ivRestoreProjects, View.SCALE_Y, 0.82F, 1.0F));
        collapseAnimatorSet.setDuration(260L);
        expandAnimatorSet.setDuration(260L);
        refreshProjectsList();
    }

    /** Filters the list by name, package or project ID (the header's search pill). */
    public void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        applyFilter();
        if (myProjects != null && myProjects.getAdapter() != null) myProjects.getAdapter().c();
    }

    private void applyFilter() {
        if (searchQuery.isEmpty()) {
            projectsList = new ArrayList<>(allProjects);
        } else {
            ArrayList<HashMap<String, Object>> filtered = new ArrayList<>();
            for (HashMap<String, Object> project : allProjects) {
                String haystack = (yB.c(project, "my_app_name") + "\n" + yB.c(project, "my_ws_name") + "\n"
                        + yB.c(project, "my_sc_pkg_name") + "\n" + yB.c(project, "sc_id")).toLowerCase(java.util.Locale.ROOT);
                if (haystack.contains(searchQuery)) filtered.add(project);
            }
            projectsList = filtered;
        }
    }

    /** "Vehicle Parking" -> "VP", "testapk" -> "TE", "my_app" -> "MA". */
    static String monogram(String name) {
        String[] words = name.trim().split("[\\s_\\-.]+|(?<=[a-z])(?=[A-Z])");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            out.appendCodePoint(word.codePointAt(0));
            if (out.codePointCount(0, out.length()) == 2) break;
        }
        if (out.codePointCount(0, out.length()) < 2) {
            String letters = name.replaceAll("[^\\p{L}\\p{N}]", "");
            if (letters.length() >= 2) {
                out.setLength(0);
                out.append(letters, 0, letters.offsetByCodePoints(0, 2));
            }
        }
        return out.length() == 0 ? "?" : out.toString().toUpperCase(java.util.Locale.ROOT);
    }

    static String androidVersion(int api) {
        switch (api) {
            case 21: return "5.0";
            case 22: return "5.1";
            case 23: return "6";
            case 24: return "7.0";
            case 25: return "7.1";
            case 26: return "8.0";
            case 27: return "8.1";
            case 28: return "9";
            case 29: return "10";
            case 30: return "11";
            case 31: return "12";
            case 32: return "12L";
            default: return api >= 33 ? String.valueOf(api - 20) : "API " + api;
        }
    }

    private void publishProjectCount() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setProjectCount(allProjects.size());
        }
    }

    public void a(boolean isEmpty) {
        // Don't load project list without having permissions
        if (!c()) {
            showCreateNewProjectLayout();
            return;
        }

        allProjects = lC.a();
        if (allProjects.size() > 0) {
            //noinspection Java8ListSort
            Collections.sort(allProjects, new ProjectComparator());
        }
        // v2.0.5: demo projects go to the Demo row (newest demo version first), not the list
        ArrayList<HashMap<String, Object>> demos = new ArrayList<>();
        for (java.util.Iterator<HashMap<String, Object>> it = allProjects.iterator(); it.hasNext(); ) {
            HashMap<String, Object> project = it.next();
            if (com.sketchware.ai.project.Demos.isDemo(project)) {
                demos.add(project);
                it.remove();
            }
        }
        com.sketchware.ai.project.Demos.sort(demos);
        renderDemos(demos);
        applyFilter();
        publishProjectCount();

        myProjects.getAdapter().c();
        if (isEmpty) showCreateNewProjectLayout();
    }

    @Override
    public void b(int requestCode) {
        if (requestCode == REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY) {
            toProjectSettingsActivity();
        } else if (requestCode == REQUEST_CODE_RESTORE_PROJECT) {
            chooseRestore();
        }
    }

    /**
     * v2.1.0 (owner's 16 Nov directive): the Demo row is gone from the projects list; demo projects are listed in
     * Restore projects > Demo (they stay out of this list).
     */
    private void renderDemos(ArrayList<HashMap<String, Object>> demos) {
        if (demoSection != null) demoSection.setVisibility(View.GONE);
    }

    private void toDesignActivity(String sc_id) {
        Intent intent = new Intent(getContext(), DesignActivity.class);
        ProjectTracker.setScId(sc_id);
        intent.putExtra("sc_id", sc_id);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivityForResult(intent, REQUEST_CODE_DESIGN_ACTIVITY);
    }

    @Override
    public void c(int requestCode) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getContext().getPackageName()));
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void d() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).s();
        }
    }

    @Override
    public void e() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).s();
        }
    }

    public int getProjectsCount() {
        return allProjects.size();
    }

    private void toExportProjectActivity(int position) {
        Intent intent = new Intent(getContext(), ExportProjectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", yB.c(projectsList.get(position), "sc_id"));
        startActivity(intent);
    }

    public void refreshProjectsList() {
        a(true);
    }

    /** v2.0.2: the project's GitHub backup link for its card (null under another signed-in account). */
    private String shareLinkOf(String scId) {
        String login = getContext() == null ? null : com.sketchware.ai.github.TokenStore.get(getContext()).login();
        return com.sketchware.ai.github.ProjectLinks.get(scId, login);
    }

    /** v2.0.3: "public" / "private" for the card's pill, or null when the project has no GitHub backup link. */
    private String visibilityOf(String scId) {
        String login = getContext() == null ? null : com.sketchware.ai.github.TokenStore.get(getContext()).login();
        return com.sketchware.ai.github.ProjectLinks.visibility(scId, login);
    }

    /**
     * v2.0.4 (owner's 16 Nov directive "Private pill opens the full-screen Publish to GitHub sheet; the Public pill offers
     * Edit public details or Switch to Private"): the Public / Private pill of a project with a GitHub repository.
     */
    private void toggleVisibility(HashMap<String, Object> project) {
        if (getActivity() == null) return;
        String scId = yB.c(project, "sc_id");
        String name = yB.c(project, "my_ws_name");
        if (com.sketchware.ai.github.TokenStore.get(getActivity()).login() == null) {
            com.sketchware.ai.github.GitHubInAppAuthDialog.show(getActivity(), login -> {
                if (getActivity() != null) refreshProjectsList();
            });
            return;
        }
        if (!"public".equals(visibilityOf(scId))) {
            com.sketchware.ai.github.PublishActivity.open(getActivity(), scId, false);
            return;
        }
        com.sketchware.ai.github.GitHubViews v = new com.sketchware.ai.github.GitHubViews(getActivity());
        LinearLayout content = new LinearLayout(getActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(v.dp(20), v.dp(8), v.dp(20), v.dp(4));
        content.addView(v.header(R.drawable.ob_ic_public, name + " is public", "Its repository and versions are open to everyone"));
        android.app.AlertDialog[] sheet = new android.app.AlertDialog[1];
        content.addView(v.actionCard(R.drawable.ob_ic_github, "Edit public details", "Description, topics, README, "
                + "release notes", x -> {
            sheet[0].dismiss();
            if (getActivity() != null) com.sketchware.ai.github.PublishActivity.open(getActivity(), scId, true);
        }), v.full(-1, 16));
        content.addView(v.actionCard(R.drawable.ob_ic_lock, "Switch to Private", "Only you can open it again, at once",
                x -> {
                    sheet[0].dismiss();
                    if (getActivity() == null) return;
                    android.content.Context app = getActivity().getApplicationContext();
                    android.widget.Toast.makeText(app, "Making " + name + " private…", android.widget.Toast.LENGTH_SHORT).show();
                    com.sketchware.ai.github.CloudSync.makePrivate(getActivity(), scId, (ok, message) -> {
                        android.widget.Toast.makeText(app, ok ? name + " is private" : message,
                                android.widget.Toast.LENGTH_LONG).show();
                        if (getActivity() != null) refreshProjectsList();
                    });
                }), v.full(-1, 10));
        sheet[0] = new com.sketchware.ai.ui.ObAlertBuilder(getActivity(), R.style.SketchwareAI_GlassAlertDialog)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * v2.0.2 review: a backup to GitHub just wrote a link: the cards show the share button at once. v2.0.3: the list is
     * read again (a visibility change, or a project the app added: the GitHub sample).
     */
    private final Runnable linksChanged = () -> {
        if (myProjects != null && myProjects.getAdapter() != null && getActivity() != null) refreshProjectsList();
    };

    /** v2.0.3: the GitHub sample project, once (made in the background when no project of its name exists). */
    private void seedGitHubSample() {
        if (getActivity() == null || !c()) return;
        android.content.Context app = getActivity().getApplicationContext();
        new Thread(() -> {
            try {
                if (com.sketchware.ai.project.GitHubSample.seedOnce(app) != null) {
                    com.sketchware.ai.github.ProjectLinks.listChanged();
                }
            } catch (Throwable t) {
                mod.jbk.util.LogUtil.e("GC", "GitHub sample not made", t);
            }
        }, "GitHubSample").start();
    }

    public void showCreateNewProjectLayout() {
        cvCreateNew.setVisibility(allProjects.size() > 0 ? View.GONE : View.VISIBLE);
    }

    private void toProjectSettingsActivity() {
        toProjectSettingsActivity(false);
    }

    private void toProjectSettingsActivity(boolean asTab) {
        // v1.8.7: new projects are made on the full-screen New Project screen (templates, colours, SDK); the
        // setting dialog (MyProjectSettingActivity) stays for the settings of existing projects.
        Intent intent = new Intent(getActivity(), com.sketchware.ai.ui.NewProjectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (asTab) com.sketchware.ai.ui.TabScreens.asTab(intent, "Create");
        startActivityForResult(intent, REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY);
    }

    /**
     * v2.1.0: the bottom bar's Create tab (the New Project screen as a tab: Back on it asks to close Create); false
     * while storage access is missing (asked for) or an update is required (the startup gate).
     */
    public boolean openCreate() {
        if (getActivity() == null) return false;
        if (!super.a(REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY)) return false;
        toProjectSettingsActivity(true);
        return true;
    }

    private void restoreProject() {
        (new BackupRestoreManager(getActivity(), this)).restore();
    }

    private void showProjectSettingDialog(int position) {
        (new ProjectSettingsDialog(getActivity(), yB.c(projectsList.get(position), "sc_id"))).show();
    }

    private void backupProject(int position) {
        if (getActivity() == null) return;
        String sc_id = yB.c(projectsList.get(position), "sc_id");
        String appName = yB.c(projectsList.get(position), "my_ws_name");
        // v2.0.1: the glass Back up sheet: the two classic boxes, "Save on phone" and "Back up to GitHub" (same .swb)
        com.sketchware.ai.github.ProjectBackupSheets.backup(getActivity(), sc_id, appName, (libraries, blocks) -> {
            if (getActivity() != null) {
                new BackupRestoreManager(getActivity()).backupNow(sc_id, appName, libraries, blocks);
            }
        });
    }

    /**
     * v2.0.1: the Restore card's glass sheet. v2.0.4 (owner's 16 Nov directive "full-screen Import & Restore page"):
     * Restore projects, full screen (GitHub projects and their versions, older backups, a link; backups on this phone).
     */
    private void chooseRestore() {
        if (getActivity() == null) return;
        startActivity(new Intent(getActivity(), com.sketchware.ai.github.GitHubBackupsActivity.class));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY) {
            if (resultCode == Activity.RESULT_OK) {
                refreshProjectsList();
                if (data.getBooleanExtra("is_new", false)) {
                    // v2.0.0: a new project goes to GitHub by itself when signed in (offline: it waits)
                    if (getActivity() != null) {
                        com.sketchware.ai.github.CloudSync.get(getActivity()).projectCreated(data.getStringExtra("sc_id"));
                    }
                    toDesignActivity(data.getStringExtra("sc_id"));
                }
            }
        } else if (requestCode == REQUEST_CODE_RESTORE_PROJECT) {
            if (resultCode == Activity.RESULT_OK) {
                refreshProjectsList();
                chooseRestore();
            }
        }
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        if ((viewId == R.id.cv_create_new || viewId == R.id.fab) && com.sketchware.ai.github.AppControl.updateRequired()
                && getActivity() != null && com.sketchware.ai.github.AppControl.showUpdateAgain(getActivity())) {
            // v2.1.0 review (MEDIUM): an update that can't wait comes first (the card as the Create tab)
            return;
        } else if ((viewId == R.id.cv_create_new || viewId == R.id.fab) && super.a(REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY)) {
            toProjectSettingsActivity();
        } else if (viewId == R.id.cv_restore_projects && super.a(REQUEST_CODE_RESTORE_PROJECT)) {
            chooseRestore();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.myprojects, parent, false);
        initialize(viewGroup);
        com.sketchware.ai.github.ProjectLinks.onChanged(linksChanged);
        seedGitHubSample();
        return viewGroup;
    }

    @SuppressLint("StaticFieldLeak")
    public class DeleteProjectTask extends MA {
        private final int position;

        public DeleteProjectTask(int position) {
            super(getContext());
            this.position = position;
            GC.this.b();
            GC.this.a(this);
        }

        @Override
        public void a() {
            if (position < projectsList.size()) {
                allProjects.remove(projectsList.remove(position));
                projectsAdapter.e(position);
                projectsAdapter.a(position, projectsAdapter.a());
                publishProjectCount();
            }

            GC.this.a();
        }

        @Override
        public void a(String idk) {
            GC.this.a();
        }

        @Override
        public void b() {
            if (position < projectsList.size()) {
                lC.a(super.a, yB.c(projectsList.get(position), "sc_id"));
            }
        }

        @Override
        protected String doInBackground(Void... voids) {
            return a(voids);
        }
    }

    public class ProjectsAdapter extends RecyclerView.a<ProjectsAdapter.ViewHolder> {
        private int layoutPosition;

        public ProjectsAdapter(RecyclerView recyclerView) {
            layoutPosition = -1;
            if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                recyclerView.a(new RecyclerView.m() {
                    @Override
                    public void a(RecyclerView recyclerView1, int var2, int var3) {
                        super.a(recyclerView1, var2, var3);
                        // v2.1.0: the bottom bar slides away while the list scrolls down and comes back on the way up
                        if (getActivity() instanceof MainActivity && (var3 > 4 || var3 < -4)) {
                            ((MainActivity) getActivity()).projectsScrolled(var3 > 4);
                        }
                        if (var3 > 4) {
                            if (isCollapsed) return;
                            collapseAnimatorSet.start();
                            isCollapsed = true;
                        } else {
                            if (var3 >= -4 || !isCollapsed) return;
                            expandAnimatorSet.start();
                            isCollapsed = false;
                        }
                    }
                });
            }
        }

        @Override
        public int a() {
            return projectsList.size();
        }

        @Override
        public void b(ViewHolder viewHolder, int position) {
            HashMap<String, Object> projectMap = projectsList.get(position);
            String scId = yB.c(projectMap, "sc_id");
            float rotation;
            int visibility;
            if (yB.a(projectMap, "expand")) {
                visibility = View.VISIBLE;
                rotation = -180.0F;
            } else {
                visibility = View.GONE;
                rotation = 0.0F;
            }
            viewHolder.projectOptionLayout.setVisibility(visibility);
            viewHolder.expand.setRotation(rotation);
            if (yB.a(projectMap, "confirmation")) {
                viewHolder.projectButtonLayout.b();
            } else {
                viewHolder.projectButtonLayout.a();
            }

            viewHolder.imgIcon.setImageDrawable(null);
            viewHolder.imgIcon.setVisibility(View.GONE);
            if (yB.c(projectMap, "sc_ver_code").isEmpty()) {
                projectMap.put("sc_ver_code", "1");
                projectMap.put("sc_ver_name", "1.0");
                lC.b(scId, projectMap);
            }

            if (yB.b(projectMap, "sketchware_ver") <= 0) {
                projectMap.put("sketchware_ver", 61);
                lC.b(scId, projectMap);
            }

            if (yB.a(projectMap, "custom_icon")) {
                Uri uri;
                String iconFolder = wq.e() + File.separator + scId;
                if (VERSION.SDK_INT >= 24) {
                    String providerPath = getContext().getPackageName() + ".provider";
                    uri = FileProvider.a(getContext(), providerPath, new File(iconFolder, "icon.png"));
                } else {
                    uri = Uri.fromFile(new File(iconFolder, "icon.png"));
                }

                viewHolder.imgIcon.setImageURI(uri);
                viewHolder.imgIcon.setVisibility(View.VISIBLE);
            }

            String appLabel = yB.c(projectMap, "my_app_name");
            viewHolder.appName.setText(yB.c(projectMap, "my_ws_name"));
            viewHolder.projectName.setText(appLabel);
            // v2.0.4: one line; a long name scrolls (marquee runs only while selected)
            viewHolder.projectName.setSelected(true);
            String version = yB.c(projectMap, "sc_ver_name") + " (" + yB.c(projectMap, "sc_ver_code") + ")";
            viewHolder.projectVersion.setText(version);
            viewHolder.packageName.setText(yB.c(projectMap, "my_sc_pkg_name") + " · " + version);

            // Monogram avatar: two alternating obsidian tints, like the design's "TA" / "VP" tiles.
            int parity;
            try {
                parity = Integer.parseInt(scId.trim());
            } catch (NumberFormatException e) {
                parity = scId.hashCode();
            }
            boolean warm = (parity & 1) == 1;
            viewHolder.appIconLayout.setBackgroundResource(warm ? R.drawable.ob_avatar_warm : R.drawable.ob_avatar);
            viewHolder.monogram.setTextColor(getResources().getColor(warm ? R.color.ob_accent : R.color.ob_accent_2));
            viewHolder.monogram.setText(monogram(appLabel.isEmpty() ? yB.c(projectMap, "my_ws_name") : appLabel));

            // What the build will really use (same defaults as the compiler).
            mod.hey.studios.project.ProjectSettings settings = new mod.hey.studios.project.ProjectSettings(scId);
            mod.hey.studios.build.BuildSettings build = new mod.hey.studios.build.BuildSettings(scId);
            int target = com.sketchware.ai.project.ModernProjectDefaults.targetSdk(settings);
            String dexer = build.getValue(mod.hey.studios.build.BuildSettings.SETTING_DEXER,
                    com.sketchware.ai.project.ModernProjectDefaults.defaultDexer());
            String java = build.getValue(mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION,
                    com.sketchware.ai.project.ModernProjectDefaults.defaultJavaVersion());
            viewHolder.badgeTarget.setText("Target " + target + " · Android " + androidVersion(target));
            viewHolder.badgeBuild.setText(dexer + " · Java " + (java.startsWith("1.") ? java.substring(2) : java));
            viewHolder.badgeMin.setText("Min " + settings.getMinSdkVersion());

            viewHolder.tvPublished.setText("#" + scId);
            // v2.0.2: the GitHub backup link's share button (stored with the project, works offline); v2.0.3: with the
            // project's Public / Private pill, under the #id
            String shared = visibilityOf(scId);
            viewHolder.githubPills.setVisibility(shared != null ? View.VISIBLE : View.GONE);
            if (shared != null) {
                boolean isPublic = "public".equals(shared);
                viewHolder.visibilityText.setText(isPublic ? "Public" : "Private");
                viewHolder.visibilityIcon.setImageResource(isPublic ? R.drawable.ob_ic_public : R.drawable.ob_ic_lock);
                viewHolder.visibility.setContentDescription(isPublic ? "Public: tap to make it private"
                        : "Private: tap to make it public");
            }
            if (target < com.sketchware.ai.project.ModernProjectDefaults.NEW_PROJECT_TARGET_SDK) {
                viewHolder.targetPill.setText("Target " + target);
                viewHolder.targetPill.setVisibility(View.VISIBLE);
                viewHolder.tvPublished.setVisibility(View.GONE);
            } else {
                viewHolder.targetPill.setVisibility(View.GONE);
                viewHolder.tvPublished.setVisibility(View.VISIBLE);
            }
            viewHolder.b.setTag("custom");
        }

        @Override
        public ViewHolder b(ViewGroup parent, int viewType) {
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext()).inflate(R.layout.myprojects_item, parent, false));
        }

        public class ViewHolder extends RecyclerView.v {
            public final TextView tvPublished;
            public final ImageView shareLink;
            public final View githubPills;
            public final View visibility;
            public final ImageView visibilityIcon;
            public final TextView visibilityText;
            public final View expand;
            public final MyProjectButtonLayout projectButtonLayout;
            public final LinearLayout projectOptionLayout;
            public final LinearLayout projectOption;
            public final LinearLayout projectOne;
            public final View appIconLayout;
            public final ImageView imgIcon;
            public final TextView monogram;
            public final TextView targetPill;
            public final TextView badgeTarget;
            public final TextView badgeBuild;
            public final TextView badgeMin;
            public final TextView projectName;
            public final TextView appName;
            public final TextView packageName;
            public final TextView projectVersion;

            public ViewHolder(View itemView) {
                super(itemView);
                projectOne = itemView.findViewById(R.id.project_one);
                projectName = itemView.findViewById(R.id.project_name);
                appIconLayout = itemView.findViewById(R.id.app_icon_layout);
                imgIcon = itemView.findViewById(R.id.img_icon);
                monogram = itemView.findViewById(R.id.ob_monogram);
                targetPill = itemView.findViewById(R.id.ob_target_pill);
                badgeTarget = itemView.findViewById(R.id.ob_badge_target);
                badgeBuild = itemView.findViewById(R.id.ob_badge_build);
                badgeMin = itemView.findViewById(R.id.ob_badge_min);
                // Custom app icons are clipped to the avatar's 16dp rounded square.
                appIconLayout.setClipToOutline(true);
                appName = itemView.findViewById(R.id.app_name);
                packageName = itemView.findViewById(R.id.package_name);
                projectVersion = itemView.findViewById(R.id.project_version);
                tvPublished = itemView.findViewById(R.id.tv_published);
                shareLink = itemView.findViewById(R.id.ob_share_link);
                shareLink.setOnClickListener(v -> {
                    int position = j();
                    if (getActivity() == null || position < 0 || position >= projectsList.size()) return;
                    HashMap<String, Object> project = projectsList.get(position);
                    String link = shareLinkOf(yB.c(project, "sc_id"));
                    if (link != null) com.sketchware.ai.github.ProjectLinks.share(getActivity(), yB.c(project, "my_ws_name"), link);
                });
                githubPills = itemView.findViewById(R.id.ob_github_pills);
                visibility = itemView.findViewById(R.id.ob_visibility);
                visibilityIcon = itemView.findViewById(R.id.ob_visibility_icon);
                visibilityText = itemView.findViewById(R.id.ob_visibility_text);
                visibility.setOnClickListener(v -> {
                    int position = j();
                    if (getActivity() == null || position < 0 || position >= projectsList.size()) return;
                    toggleVisibility(projectsList.get(position));
                });
                expand = itemView.findViewById(R.id.expand);
                projectOptionLayout = itemView.findViewById(R.id.project_option_layout);
                projectOption = itemView.findViewById(R.id.project_option);
                projectButtonLayout = new MyProjectButtonLayout(getContext());
                projectOption.addView(projectButtonLayout);
                projectButtonLayout.setButtonOnClickListener(v -> {
                    if (!mB.a()) {
                        layoutPosition = j();
                        if (layoutPosition <= projectsList.size()) {
                            HashMap<String, Object> projectMap = projectsList.get(layoutPosition);
                            if (v instanceof MyProjectButton) {
                                switch (((MyProjectButton) v).b) {
                                    case 0:
                                        toProjectSettingOrRequestPermission(layoutPosition);
                                        break;

                                    case 1:
                                        backupProject(layoutPosition);
                                        break;

                                    case 2:
                                        toExportProjectActivity(layoutPosition);
                                        break;

                                    case 3:
                                        projectMap.put("confirmation", true);
                                        projectButtonLayout.b();
                                        break;

                                    case 4:
                                        showProjectSettingDialog(layoutPosition);
                                        break;
                                }
                            } else {
                                if (v.getId() == R.id.confirm_yes) {
                                    projectMap.put("confirmation", false);
                                    projectMap.put("expand", false);
                                    (new DeleteProjectTask(layoutPosition)).execute();
                                } else if (v.getId() == R.id.confirm_no) {
                                    projectMap.put("confirmation", false);
                                    ProjectsAdapter.this.c(layoutPosition);
                                }

                            }
                        }
                    }
                });
                projectOne.setOnClickListener(v -> {
                    if (!mB.a()) {
                        layoutPosition = j();
                        toDesignActivity(yB.c(projectsList.get(layoutPosition), "sc_id"));
                    }
                });
                projectOne.setOnLongClickListener(v -> {
                    layoutPosition = j();
                    if (yB.a(projectsList.get(layoutPosition), "expand")) {
                        collapse();
                    } else {
                        expand();
                    }

                    return true;
                });
                appIconLayout.setOnClickListener(v -> {
                    mB.a(v);
                    layoutPosition = j();
                    toProjectSettingOrRequestPermission(layoutPosition);
                });
                expand.setOnClickListener(v -> {
                    if (!mB.a()) {
                        layoutPosition = j();
                        if (yB.a(projectsList.get(layoutPosition), "expand")) {
                            collapse();
                        } else {
                            expand();
                        }
                    }
                });
                // The ID / target pill column also opens the action grid (bigger touch target).
                targetPill.setOnClickListener(v -> expand.performClick());
                tvPublished.setOnClickListener(v -> expand.performClick());
            }

            public void collapse() {
                projectsList.get(layoutPosition).put("expand", false);
                gB.a(expand, 0.0F, null);
                gB.a(projectOptionLayout, 300, new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        projectOptionLayout.setVisibility(View.GONE);
                    }
                });
            }

            public void expand() {
                projectOptionLayout.setVisibility(View.VISIBLE);
                projectsList.get(layoutPosition).put("expand", true);
                gB.a(expand, -180.0F, null);
                gB.b(projectOptionLayout, 300, null);
            }
        }
    }
}
