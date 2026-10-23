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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    /** Every project on disk; {@link #projectsList} is what the search filter lets through. */
    private ArrayList<HashMap<String, Object>> allProjects = new ArrayList<>();
    private String searchQuery = "";
    private Boolean isCollapsed;
    private AnimatorSet collapseAnimatorSet;
    private AnimatorSet expandAnimatorSet;
    private ProjectsAdapter projectsAdapter;
    private FloatingActionButton floatingActionButton;

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
        floatingActionButton = getActivity().findViewById(R.id.fab);
        floatingActionButton.setOnClickListener(this);

        myProjects = parent.findViewById(R.id.myprojects);
        myProjects.setHasFixedSize(true);
        myProjects.setLayoutManager(new LinearLayoutManager(getContext()));
        projectsAdapter = new ProjectsAdapter(myProjects);
        myProjects.setAdapter(projectsAdapter);
        myProjects.setItemAnimator(new ci());

        cvCreateNew = parent.findViewById(R.id.cv_create_new);
        cvCreateNew.setOnClickListener(this);

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
            restoreProject();
        }
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

    public void showCreateNewProjectLayout() {
        if (allProjects.size() > 0) {
            cvCreateNew.setVisibility(View.GONE);
            floatingActionButton.f();
        } else {
            cvCreateNew.setVisibility(View.VISIBLE);
            floatingActionButton.c();
        }
    }

    private void toProjectSettingsActivity() {
        // v1.8.7: new projects are made on the full-screen New Project screen (templates, colours, SDK); the
        // setting dialog (MyProjectSettingActivity) stays for the settings of existing projects.
        Intent intent = new Intent(getActivity(), com.sketchware.ai.ui.NewProjectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivityForResult(intent, REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY);
    }

    private void restoreProject() {
        (new BackupRestoreManager(getActivity(), this)).restore();
    }

    private void showProjectSettingDialog(int position) {
        (new ProjectSettingsDialog(getActivity(), yB.c(projectsList.get(position), "sc_id"))).show();
    }

    private void backupProject(int position) {
        String sc_id = yB.c(projectsList.get(position), "sc_id");
        String appName = yB.c(projectsList.get(position), "my_ws_name");
        (new BackupRestoreManager(getActivity())).backup(sc_id, appName);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY) {
            if (resultCode == Activity.RESULT_OK) {
                refreshProjectsList();
                if (data.getBooleanExtra("is_new", false)) {
                    toDesignActivity(data.getStringExtra("sc_id"));
                }
            }
        } else if (requestCode == REQUEST_CODE_RESTORE_PROJECT) {
            if (resultCode == Activity.RESULT_OK) {
                refreshProjectsList();
                restoreProject();
            }
        }
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        if ((viewId == R.id.cv_create_new || viewId == R.id.fab) && super.a(REQUEST_CODE_PROJECT_SETTINGS_ACTIVITY)) {
            toProjectSettingsActivity();
        } else if (viewId == R.id.cv_restore_projects && super.a(REQUEST_CODE_RESTORE_PROJECT)) {
            restoreProject();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.myprojects, parent, false);
        initialize(viewGroup);
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
