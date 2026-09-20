package com.besome.sketch;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.besome.sketch.lib.base.BasePermissionAppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.sketchware.ai.migration.ImportProgressDialog;
import com.sketchware.ai.migration.LegacyImportController;
import com.sketchware.ai.migration.LegacyProjectMigrator;
import com.sketchware.ai.storage.StorageAccess;
import com.sketchware.remod.R;

import java.io.File;
import java.io.IOException;

import a.a.a.DB;
import a.a.a.GB;
import a.a.a.GC;
import a.a.a.Xf;
import a.a.a.aB;
import a.a.a.bB;
import a.a.a.gg;
import a.a.a.l;
import a.a.a.nd;
import a.a.a.oB;
import a.a.a.sB;
import a.a.a.wq;
import a.a.a.xB;
import mod.SketchwareUtil;
import mod.agus.jcoderz.lib.FileUtil;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.ilyasse.activities.about.AboutModActivity;
import mod.jbk.util.LogUtil;
import mod.tyron.backup.CallBackTask;
import mod.tyron.backup.SingleCopyAsyncTask;

public class MainActivity extends BasePermissionAppCompatActivity implements ViewPager.e {

    private FloatingActionButton fab;
    private DrawerLayout drawerLayout;
    private l drawerToggle;
    private MainDrawer drawer;
    private ViewPager viewPager;
    private DB u;
    private CoordinatorLayout coordinator;
    private Snackbar storageAccessDenied;
    private GC projectsFragment = null;
    /** Guards against stacking the import offer when onResume runs repeatedly. */
    private AlertDialog importOffer;
    private ImportProgressDialog importProgress;
    private final LegacyImportController.Listener importListener = new LegacyImportController.Listener() {
        @Override
        public void onProgress(LegacyProjectMigrator.Progress progress) {
            LegacyImportController controller = LegacyImportController.get(MainActivity.this);
            if (!controller.isInBackground()) {
                showImportProgress().update(progress);
            }
        }

        @Override
        public void onFinished(LegacyProjectMigrator.Result result) {
            onLegacyImportFinished(result);
        }
    };

    @Override
    // ViewPager.OnPageChangeListener#onPageScrollStateChanged(int)
    public void a(int state) {
    }

    @Override
    // ViewPager.OnPageChangeListener#onPageScrolled(int, float, int)
    public void a(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    // onRequestPermissionsResult but for Storage access only, and only when granted
    public void g(int i) {
        if (i == 9501) {
            if (storageAccessDenied != null && storageAccessDenied.j()) {
                storageAccessDenied.c();
            }
            maybeOfferLegacyImport();

            if (viewPager.getCurrentItem() == 0 && projectsFragment != null) {
                projectsFragment.refreshProjectsList();
            }
        }
    }

    @Override
    public void h(int i) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
        startActivityForResult(intent, i);
    }

    @Override
    public void l() {
        s();
    }

    private void selectPageZero() {
        if (viewPager != null) {
            // ViewPager#setCurrentItem(int, boolean)
            viewPager.a(0, true);
        }
    }

    @Override
    public void m() {
        s();
    }

    public void n() {
        if (projectsFragment != null) {
            projectsFragment.a(false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        invalidateOptionsMenu();
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case 105:
                    sB.a(this, data.getBooleanExtra("onlyConfig", true));
                    selectPageZero();
                    break;

                case 111:
                    invalidateOptionsMenu();
                    break;

                case 113:
                    if (data != null && data.getBooleanExtra("not_show_popup_anymore", false)) {
                        u.a("U1I2", (Object) false);
                    }
                    break;

                case 212:
                    if (!(data.getStringExtra("save_as_new_id") == null ? "" : data.getStringExtra("save_as_new_id")).isEmpty() && j()) {
                        projectsFragment.refreshProjectsList();
                    }
                    break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isShown()) {
            // DrawerLayout#closeDrawers()
            drawerLayout.b();
        } else {
            finish();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // ActionBarDrawerToggle#onConfigurationChanged(Configuration)
        drawerToggle.a(newConfig);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tryLoadingCustomizedAppStrings();
        setContentView(R.layout.main);

        u = new DB(getApplicationContext(), "U1");
        int u1I0 = u.a("U1I0", -1);
        long u1I1 = u.e("U1I1");
        if (u1I1 <= 0) {
            u.a("U1I1", System.currentTimeMillis());
        }
        if (System.currentTimeMillis() - u1I1 > /* (a day) */ 1000 * 60 * 60 * 24) {
            u.a("U1I0", Integer.valueOf(u1I0 + 1));
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        a(toolbar);
        // The glass header draws its own menu pill, so the action bar's home arrow stays hidden.
        d().d(false);
        d().e(false);
        com.sketchware.ai.ui.ObsidianWindow.apply(this);
        setUpGlassHeader();
        ImageView logo = findViewById(R.id.img_title_logo);
        logo.setOnClickListener(v -> invalidateOptionsMenu());
        drawer = findViewById(R.id.left_drawer);
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerToggle = new l(this, drawerLayout, R.string.app_name, R.string.app_name);
        // DrawerLayout#addDrawerListener(DrawerLayout.DrawerListener)
        drawerLayout.a((DrawerLayout.c) drawerToggle);
        d().a("");

        viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));
        // ViewPager#addOnPageChangeListener(ViewPager.OnPageChangeListener)
        viewPager.a(this);

        fab = findViewById(R.id.fab);
        coordinator = findViewById(R.id.layout_coordinator);

        // Nothing touches /sdcard/.sketchware_ai (and no import starts) until access is granted.
        // Without it, the user is sent to the system settings; onResume re-checks on return.
        boolean hasStorageAccess = j();
        if (!hasStorageAccess) {
            showNoticeNeedStorageAccess();
        }

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Uri data = getIntent().getData();
            if (data != null) {
                // TODO: Progress indicator while restoring project, possibly from background + notifications
                new SingleCopyAsyncTask(data, this, new CallBackTask() {
                    @Override
                    public void onCopyPreExecute() {
                    }

                    @Override
                    public void onCopyProgressUpdate(int progress) {
                    }

                    @Override
                    public void onCopyPostExecute(String path, boolean wasSuccessful, String reason) {
                        if (wasSuccessful) {
                            BackupRestoreManager manager = new BackupRestoreManager(MainActivity.this, projectsFragment);

                            if (BackupFactory.zipContainsFile(path, "local_libs")) {
                                new com.sketchware.ai.ui.ObAlertBuilder(MainActivity.this)
                                        .setTitle("Warning")
                                        .setMessage(BackupRestoreManager.getRestoreIntegratedLocalLibrariesMessage(false, -1, -1, null))
                                        .setPositiveButton("Copy", (dialog, which) -> manager.doRestore(path, true))
                                        .setNegativeButton("Don't copy", (dialog, which) -> manager.doRestore(path, false))
                                        .setNeutralButton(R.string.common_word_cancel, null)
                                        .show();
                            } else {
                                manager.doRestore(path, true);
                            }

                            // Clear intent so it doesn't duplicate
                            getIntent().setData(null);
                        } else {
                            SketchwareUtil.toastError("Failed to copy backup file to temporary location: " + reason, Toast.LENGTH_LONG);
                        }
                    }
                }).execute(data);
            }
        } else if (hasStorageAccess && !LegacyProjectMigrator.shouldOffer(this)
                && !ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_SKIP_MAJOR_CHANGES_REMINDER)) {
            aB dialog = new aB(this);
            dialog.b("New changes in v6.4.0");
            dialog.a("Just as a reminder; There have been many changes since v6.3.0 fix1, " +
                    "and it's important to know them all if you want your projects to still work.\n" +
                    "You can view all changes whenever you want at the updated About Sketchware Pro screen.");

            dialog.b("View", v -> {
                dialog.dismiss();
                Intent launcher = new Intent(this, AboutModActivity.class);
                launcher.putExtra("select", "majorChanges");
                startActivity(launcher);
            });
            dialog.a("Close", Helper.getDialogDismissListener(dialog));
            dialog.configureDefaultButton("Never show again", v -> {
                ConfigActivity.setSetting(ConfigActivity.SETTING_SKIP_MAJOR_CHANGES_REMINDER, true);
                dialog.dismiss();
            });
            dialog.show();
        }
    }

    @Override
    public void onDestroy() {
        if (importProgress != null) importProgress.dismiss();
        if (importOffer != null && importOffer.isShowing()) importOffer.dismiss();
        super.onDestroy();
        xB.b().a();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(LegacyImportController.EXTRA_SHOW_IMPORT_PROGRESS, false)) {
            LegacyImportController controller = LegacyImportController.get(this);
            if (controller.isRunning()) controller.moveToForeground();
        }
    }

    @Override
    public void onPause() {
        LegacyImportController.get(this).detach(importListener);
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // ActionBarDrawerToggle#onOptionsItemSelected(MenuItem)
        if (drawerToggle.a(item)) {
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // ActionBarDrawerToggle#syncState()
        drawerToggle.b();
    }

    @Override
    public void onResume() {
        super.onResume();
        /* Check if the device is running low on storage space */
        long freeMegabytes = GB.c();
        if (freeMegabytes < 100 && freeMegabytes > 0) {
            showNoticeNotEnoughFreeStorageSpace();
        }
        if (j() && storageAccessDenied != null && storageAccessDenied.j()) {
            storageAccessDenied.c();
        }

        LegacyImportController controller = LegacyImportController.get(this);
        if (getIntent() != null && getIntent().getBooleanExtra(LegacyImportController.EXTRA_SHOW_IMPORT_PROGRESS, false)) {
            getIntent().removeExtra(LegacyImportController.EXTRA_SHOW_IMPORT_PROGRESS);
            if (controller.isRunning()) controller.moveToForeground();
        }
        if (controller.isRunning() && !controller.isInBackground()) {
            showImportProgress();
        }
        controller.attach(importListener);
        if (j()) {
            maybeOfferLegacyImport();
        }
    }

    /**
     * Offers to copy projects from a Sketchware Pro install into Sketchware AI's own folder.
     * Only ever shown once storage access is granted, never while an import runs, and at most
     * once at a time. Offered again after an interrupted import, to resume it.
     */
    private void maybeOfferLegacyImport() {
        if (!StorageAccess.isGranted(this) || isFinishing()) return;
        if (importOffer != null && importOffer.isShowing()) return;
        LegacyImportController controller = LegacyImportController.get(this);
        if (controller.isRunning() || !LegacyProjectMigrator.shouldOffer(this)) return;

        int count = LegacyProjectMigrator.legacyProjectCount();
        boolean resume = LegacyProjectMigrator.wasInterrupted(this);
        importOffer = new com.sketchware.ai.ui.ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle(resume ? "Resume import from Sketchware Pro" : "Import from Sketchware Pro")
                .setMessage(resume
                        ? "The last import from Sketchware Pro did not finish. Continue it? "
                        + "Projects that are already imported are skipped."
                        : "Found " + count + (count == 1 ? " project" : " projects")
                        + " in Sketchware Pro's folder (/.sketchware).\n\n"
                        + "Copy them, with your custom blocks, components and local libraries, "
                        + "into Sketchware AI? The copy runs in the background with progress; "
                        + "Sketchware Pro's files are not changed.")
                .setPositiveButton(resume ? "Resume" : "Import", (dialog, which) -> startLegacyImport())
                .setNegativeButton("Not now", null)
                .setNeutralButton("Don't ask again",
                        (dialog, which) -> LegacyProjectMigrator.markDeclined(this))
                .create();
        importOffer.setCanceledOnTouchOutside(false);
        importOffer.show();
    }

    private void startLegacyImport() {
        if (!StorageAccess.isGranted(this)) {
            showNoticeNeedStorageAccess();
            return;
        }
        LegacyImportController controller = LegacyImportController.get(this);
        showImportProgress();
        controller.attach(importListener);
        controller.start();
    }

    private ImportProgressDialog showImportProgress() {
        if (importProgress == null) {
            importProgress = new ImportProgressDialog(this, new ImportProgressDialog.Callbacks() {
                @Override
                public void onRunInBackground() {
                    requestNotificationPermissionIfNeeded();
                    LegacyImportController.get(MainActivity.this).moveToBackground();
                    SketchwareUtil.toast("Import continues in the background", Toast.LENGTH_SHORT);
                }

                @Override
                public void onStop() {
                    LegacyImportController.get(MainActivity.this).cancel();
                }
            });
        }
        LegacyProjectMigrator.Progress last = LegacyImportController.get(this).lastProgress();
        if (last != null) importProgress.update(last);
        if (!isFinishing()) importProgress.show();
        return importProgress;
    }

    /** Android 13+: the background progress notification needs POST_NOTIFICATIONS. */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            String permission = "android.permission.POST_NOTIFICATIONS";
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{permission}, 9502);
            }
        }
    }

    private void onLegacyImportFinished(LegacyProjectMigrator.Result result) {
        if (importProgress != null) {
            importProgress.dismiss();
            importProgress = null;
        }
        if (isFinishing()) return;
        if (result.isSuccess()) {
            SketchwareUtil.toast(ImportProgressDialog.summary(result), Toast.LENGTH_LONG);
        } else {
            new com.sketchware.ai.ui.ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                    .setTitle(result.cancelled ? "Import stopped" : "Import failed")
                    .setMessage(ImportProgressDialog.summary(result))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
        if (projectsFragment != null) {
            projectsFragment.refreshProjectsList();
        }
    }

    private void showNoticeNeedStorageAccess() {
        aB dialog = new aB(this);
        dialog.b(Helper.getResString(R.string.common_message_permission_title_storage));
        dialog.a(R.drawable.color_about_96);
        if (StorageAccess.usesAllFilesAccess()) {
            dialog.a("Sketchware AI keeps your projects, libraries and builds in /.sketchware_ai. "
                    + "On Android 11 and newer this needs \"All files access\".\n\n"
                    + "Tap Settings, switch on \"Allow access to manage all files\", then come back.");
            dialog.b(Helper.getResString(R.string.common_word_settings), v -> {
                dialog.dismiss();
                requestStorageAccess(9501);
            });
            dialog.a(Helper.getResString(R.string.common_word_cancel), v -> {
                dialog.dismiss();
                s();
            });
        } else {
            dialog.a(Helper.getResString(R.string.common_message_permission_need_load_project));
            dialog.b(Helper.getResString(R.string.common_word_ok), v -> {
                dialog.dismiss();
                requestStorageAccess(9501);
            });
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showNoticeNotEnoughFreeStorageSpace() {
        aB dialog = new aB(this);
        dialog.b(Helper.getResString(R.string.common_message_insufficient_storage_space_title));
        dialog.a(R.drawable.high_priority_96_red);
        dialog.a(Helper.getResString(R.string.common_message_insufficient_storage_space));
        dialog.b(Helper.getResString(R.string.common_word_ok),
                Helper.getDialogDismissListener(dialog));
        dialog.show();
    }

    public void s() {
        if (storageAccessDenied == null || !storageAccessDenied.j()) {
            storageAccessDenied = Snackbar.a(coordinator, Helper.getResString(R.string.common_message_permission_denied), -2);
            storageAccessDenied.a(Helper.getResString(R.string.common_word_settings), v -> {
                storageAccessDenied.c();
                requestStorageAccess(9501);
            });
            storageAccessDenied.f(Color.YELLOW);
            storageAccessDenied.n();
        }
    }

    @Override
    // ViewPager.OnPageChangeListener#onPageSelected(int)
    public void b(int position) {
        if (position == 0) {
            if (j() && projectsFragment != null && projectsFragment.getProjectsCount() == 0) {
                projectsFragment.refreshProjectsList();
            }
            projectsFragment.showCreateNewProjectLayout();
        } else if (position == 1) {
            fab.c();
        }
    }

    /** Glass header: menu pill opens the drawer, search pill swaps the title for a live filter. */
    private void setUpGlassHeader() {
        View titleBlock = findViewById(R.id.ob_title_block);
        android.widget.EditText searchField = findViewById(R.id.ob_search_field);
        ImageView search = findViewById(R.id.ob_search);
        findViewById(R.id.ob_menu).setOnClickListener(v -> drawerLayout.h(android.view.Gravity.START));
        search.setOnClickListener(v -> {
            boolean open = searchField.getVisibility() != View.VISIBLE;
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (open) {
                titleBlock.setVisibility(View.GONE);
                searchField.setVisibility(View.VISIBLE);
                search.setImageResource(R.drawable.ob_ic_close);
                search.setContentDescription("Close search");
                searchField.requestFocus();
                imm.showSoftInput(searchField, 0);
            } else {
                searchField.setText("");
                imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
                searchField.setVisibility(View.GONE);
                titleBlock.setVisibility(View.VISIBLE);
                search.setImageResource(R.drawable.ob_ic_search);
                search.setContentDescription("Search projects");
            }
        });
        searchField.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (projectsFragment != null) projectsFragment.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }

    /** Header subtitle, e.g. "5 projects · Android 17 ready". */
    public void setProjectCount(int count) {
        TextView subtitle = findViewById(R.id.ob_subtitle);
        if (subtitle != null) {
            subtitle.setText(count + (count == 1 ? " project" : " projects") + " · Android 17 ready");
        }
    }

    private void tryLoadingCustomizedAppStrings() {
        // Refresh extracted provided strings file if necessary
        oB oB = new oB();
        try {
            File extractedStringsProvidedXml = new File(wq.m());
            if (oB.a(getApplicationContext(), "localization/strings.xml") !=
                    (extractedStringsProvidedXml.exists() ? extractedStringsProvidedXml.length() : 0)) {
                oB.a(extractedStringsProvidedXml);
                oB.a(getApplicationContext(), "localization/strings.xml", wq.m());
            }
        } catch (Exception e) {
            String message = "Couldn't extract default strings to storage";
            SketchwareUtil.toastError(message + ": " + e.getMessage());
            LogUtil.e("MainActivity", message, e);
        }

        // Actual loading part
        if (xB.b().b(getApplicationContext())) {
            bB.a(getApplicationContext(),
                    Helper.getResString(R.string.message_strings_xml_loaded),
                    0, 80, 0, 128).show();
        }
    }

    private class PagerAdapter extends gg {

        public PagerAdapter(Xf xf) {
            super(xf);
        }

        @Override
        // PagerAdapter#getCount()
        public int a() {
            return 1;
        }

        @Override
        // FragmentPagerAdapter#instantiateItem(ViewGroup, int)
        public Object a(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.a(container, position);
            projectsFragment = (GC) fragment;
            return fragment;
        }

        @Override
        // FragmentPagerAdapter#getItem(int)
        public Fragment c(int position) {
            return new GC();
        }

        @Override
        // PagerAdapter#getPageTitle(int)
        public CharSequence a(int position) {
            return Helper.getResString(R.string.main_tab_title_myproject);
        }
    }
}
