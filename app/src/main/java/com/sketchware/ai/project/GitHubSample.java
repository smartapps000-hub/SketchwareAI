package com.sketchware.ai.project;

import android.content.Context;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.TextBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.lC;
import a.a.a.yB;

/**
 * v2.0.4: the list of repositories and files (showList), downloads with progress, the profile as readable text; user
 * names with or without "@".
 * <p>
 * v2.0.3 (owner's 15 Nov directive "ready-to-run sample test project"): "GitHub API Demo", a project made the way the New
 * Project screen makes one ({@link NewProjectSetup#create}, Empty Activity, Material 3), then filled with: a text field
 * for a GitHub user name, "Load profile" and "List repositories" buttons, an output text, the GitHub component
 * {@code github1}, a String variable {@code userName}, and blocks: the buttons read the field into the variable and call
 * get user / list repositories; the component's events "user loaded", "repositories listed" and "error" show the answer
 * in the text and a Toast. No token: public data only (GitHub allows 60 requests an hour per network without one).
 * Made from the GitHub blocks guide's button, and once by itself (the projects list, when no project of that name
 * exists).
 */
public final class GitHubSample {

    public static final String NAME = "GitHub API Demo";
    public static final String PROJECT_NAME = "GitHub_API_Demo";
    public static final String PACKAGE = "com.my.githubdemo";
    private static final String SEEDED = "github_sample_seeded";

    private GitHubSample() {
    }

    /** True when a project of that name is in the list. */
    public static boolean exists() {
        for (HashMap<String, Object> project : lC.a()) {
            if (PROJECT_NAME.equals(yB.c(project, "my_ws_name"))) return true;
        }
        return false;
    }

    /**
     * Once per install: the sample is made when no project of its name exists (worker thread; storage access granted).
     * Returns the new project's id, or null.
     */
    public static synchronized String seedOnce(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("sketchware_ai_cloud_sync",
                Context.MODE_PRIVATE);
        if (prefs.getBoolean(SEEDED, false)) return null;
        prefs.edit().putBoolean(SEEDED, true).apply();
        if (exists()) return null;
        return create(context);
    }

    /** Makes the sample project (worker thread) and returns its id. */
    public static synchronized String create(Context context) {
        return create(context, ProjectIds.free(lC.b()));
    }

    /** The same with the project id given (the harness's fixture uses a fixed one). */
    public static String create(Context context, String scId) {
        NewProjectSetup.Choices choices = new NewProjectSetup.Choices();
        choices.appName = NAME;
        choices.projectName = PROJECT_NAME;
        choices.packageName = PACKAGE;
        try {
            choices.sketchwareVersion = a.a.a.GB.d(context);
        } catch (RuntimeException ignored) {
            // the default version number
        }
        try {
            NewProjectSetup.create(context, scId, choices);
            fill(scId);
        } catch (RuntimeException | Error e) {
            // no half-made project in the list
            try {
                lC.a(context, scId);
            } catch (Throwable ignored) {
            }
            throw e;
        }
        return scId;
    }

    /** The views, the component, the variable, the events and the blocks (own instances, saved at the end). */
    static void fill(String scId) {
        hC files = new hC(scId);
        if (files.g()) files.h();
        else files.i();
        eC project = new eC(scId);
        if (project.d()) project.h();
        else project.g();
        if (project.c()) project.f();
        else project.e();

        ProjectFileBean main = files.b().get(0);
        String xml = main.getXmlName();
        String java = main.getJavaName();

        ViewBean column = view(project, xml, "linear1", ViewBean.VIEW_TYPE_LAYOUT_LINEAR, "LinearLayout", null, 0);
        column.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
        column.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        column.layout.paddingLeft = 16;
        column.layout.paddingTop = 16;
        column.layout.paddingRight = 16;
        column.layout.paddingBottom = 16;
        ViewBean title = view(project, xml, "textview1", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView", "linear1", 0);
        title.text.text = "GitHub API Demo";
        title.text.textSize = 22;
        title.text.textType = TextBean.TEXT_TYPE_BOLD;
        ViewBean field = view(project, xml, "edittext1", ViewBean.VIEW_TYPE_WIDGET_EDITTEXT, "EditText", "linear1", 1);
        field.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        field.layout.marginTop = 12;
        field.text.text = "";
        field.text.hint = "GitHub user name (e.g. octocat)";
        ViewBean profile = view(project, xml, "button1", ViewBean.VIEW_TYPE_WIDGET_BUTTON, "Button", "linear1", 2);
        profile.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        profile.layout.marginTop = 8;
        profile.text.text = "Load profile";
        ViewBean repos = view(project, xml, "button2", ViewBean.VIEW_TYPE_WIDGET_BUTTON, "Button", "linear1", 3);
        repos.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        repos.text.text = "List repositories";
        ViewBean output = view(project, xml, "textview2", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView", "linear1", 4);
        output.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        output.layout.marginTop = 12;
        output.text.text = "Type a GitHub user name (with or without @) and tap a button. No token needed.";
        output.text.textSize = 14;
        // v2.0.4 (owner's 16 Nov directive "a clean formatted repository / file list, tap to view files or download"):
        // the list the GitHub component fills (showList): repositories, then a repository's files; a file downloads
        ViewBean listView = view(project, xml, "listview1", ViewBean.VIEW_TYPE_WIDGET_LISTVIEW, "ListView", "linear1", 5);
        listView.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        listView.layout.height = LayoutBean.LAYOUT_MATCH_PARENT;
        listView.layout.marginTop = 8;

        project.h.computeIfAbsent(java, k -> new ArrayList<>()).add(new ComponentBean(GitHubCode.TYPE, "github1"));
        project.e.computeIfAbsent(java, k -> new ArrayList<>()).add(new Pair<>(2, "userName"));
        ArrayList<EventBean> events = project.i.computeIfAbsent(java, k -> new ArrayList<>());
        events.add(new EventBean(EventBean.EVENT_TYPE_VIEW, ViewBean.VIEW_TYPE_WIDGET_BUTTON, "button1", "onClick"));
        events.add(new EventBean(EventBean.EVENT_TYPE_VIEW, ViewBean.VIEW_TYPE_WIDGET_BUTTON, "button2", "onClick"));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_USER));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_REPOS));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_ERROR));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_FILES));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_PROGRESS));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_SAVED));

        // each button: userName = the field's text; then the GitHub call; a Toast says it is on its way
        project.a(java, "button1_onClick", ask("gitHubGetUser", "%m.GitHub get user %s.userOrEmptyForMe",
                "Loading the profile…"));
        project.a(java, "button2_onClick", ask("gitHubListRepos", "%m.GitHub list repositories of %s.userOrEmptyForMine",
                "Loading the repositories…"));

        // user loaded: the profile as readable text (name, @login, bio, repositories, followers), the login in a Toast
        ArrayList<BlockBean> user = new ArrayList<>();
        BlockBean show = block("10", "%m.textview setText %s", " ", "setText", "textview2", "@20");
        show.nextBlock = 11;
        user.add(show);
        user.add(block("20", "%m.GitHub readable text of %s.json", "s", GitHubCode.OP_DESCRIBE, "github1", "@21"));
        user.add(block("21", "json", "s", "getArg"));
        user.add(block("11", "Toast %s", " ", "doToast", "@25"));
        user.add(block("25", "%s join %s", "s", "stringJoin", "Loaded @", "@26"));
        user.add(block("26", "login", "s", "getArg"));
        project.a(java, "github1_" + GitHubCode.EVENT_USER, user);

        // repositories listed: shown as rows in the list (tap one for its files), the count above it and in a Toast
        ArrayList<BlockBean> listed = new ArrayList<>();
        BlockBean rows = block("10", "%m.GitHub show %s.json in %m.listview", " ", GitHubCode.OP_SHOW_LIST, "github1",
                "@20", "listview1");
        rows.nextBlock = 11;
        listed.add(rows);
        listed.add(block("20", "reposJson", "s", "getArg"));
        BlockBean count = block("11", "%m.textview setText %s", " ", "setText", "textview2", "@21");
        count.nextBlock = 12;
        listed.add(count);
        listed.add(block("21", "%s join %s", "s", "stringJoin", "@22", " repositories: tap one for its files"));
        listed.add(block("22", "toString %d without decimal", "s", "toString", "@23"));
        listed.add(block("23", "count", "d", "getArg"));
        listed.add(block("12", "Toast %s", " ", "doToast", "@24"));
        listed.add(block("24", "%s join %s", "s", "stringJoin", "@25", " repositories"));
        listed.add(block("25", "toString %d without decimal", "s", "toString", "@26"));
        listed.add(block("26", "count", "d", "getArg"));
        project.a(java, "github1_" + GitHubCode.EVENT_REPOS, listed);

        // files listed: a repository's (or folder's) files in the same list; a tap on a file downloads it
        ArrayList<BlockBean> filesListed = new ArrayList<>();
        BlockBean fileRows = block("10", "%m.GitHub show %s.json in %m.listview", " ", GitHubCode.OP_SHOW_LIST, "github1",
                "@20", "listview1");
        fileRows.nextBlock = 11;
        filesListed.add(fileRows);
        filesListed.add(block("20", "filesJson", "s", "getArg"));
        filesListed.add(block("11", "%m.textview setText %s", " ", "setText", "textview2", "@21"));
        filesListed.add(block("21", "%s join %s", "s", "stringJoin", "@22", "@24"));
        filesListed.add(block("22", "%s join %s", "s", "stringJoin", "@23", ": "));
        filesListed.add(block("23", "repo", "s", "getArg"));
        filesListed.add(block("24", "%s join %s", "s", "stringJoin", "@25", " files and folders: tap a file to download it"));
        filesListed.add(block("25", "toString %d without decimal", "s", "toString", "@26"));
        filesListed.add(block("26", "count", "d", "getArg"));
        project.a(java, "github1_" + GitHubCode.EVENT_FILES, filesListed);

        // download progress: the percentage above the list
        ArrayList<BlockBean> progress = new ArrayList<>();
        progress.add(block("10", "%m.textview setText %s", " ", "setText", "textview2", "@20"));
        progress.add(block("20", "%s join %s", "s", "stringJoin", "Downloading… ", "@21"));
        progress.add(block("21", "%s join %s", "s", "stringJoin", "@22", "%"));
        progress.add(block("22", "toString %d without decimal", "s", "toString", "@23"));
        progress.add(block("23", "percent", "d", "getArg"));
        project.a(java, "github1_" + GitHubCode.EVENT_PROGRESS, progress);

        // file saved: where it went, above the list and in a Toast
        ArrayList<BlockBean> saved = new ArrayList<>();
        BlockBean where = block("10", "%m.textview setText %s", " ", "setText", "textview2", "@20");
        where.nextBlock = 11;
        saved.add(where);
        saved.add(block("20", "%s join %s", "s", "stringJoin", "Saved: ", "@21"));
        saved.add(block("21", "path", "s", "getArg"));
        saved.add(block("11", "Toast %s", " ", "doToast", "@22"));
        saved.add(block("22", "%s join %s", "s", "stringJoin", "Saved to ", "@23"));
        saved.add(block("23", "path", "s", "getArg"));
        project.a(java, "github1_" + GitHubCode.EVENT_SAVED, saved);

        // error: GitHub's answer explained, in the output and a Toast
        ArrayList<BlockBean> error = new ArrayList<>();
        BlockBean message = block("10", "%m.textview setText %s", " ", "setText", "textview2", "@20");
        message.nextBlock = 11;
        error.add(message);
        error.add(block("20", "message", "s", "getArg"));
        error.add(block("11", "Toast %s", " ", "doToast", "@21"));
        error.add(block("21", "%s join %s", "s", "stringJoin", "GitHub: ", "@22"));
        error.add(block("22", "action", "s", "getArg"));
        project.a(java, "github1_" + GitHubCode.EVENT_ERROR, error);

        files.m();
        project.j();
    }

    /** set userName to edittext1's text → the GitHub block with userName → a Toast. */
    private static ArrayList<BlockBean> ask(String opCode, String spec, String toast) {
        ArrayList<BlockBean> blocks = new ArrayList<>();
        BlockBean set = block("10", "set %m.varStr to %s", " ", "setVarString", "userName", "@20");
        set.nextBlock = 11;
        blocks.add(set);
        blocks.add(block("20", "%m.textview getText", "s", "getText", "edittext1"));
        BlockBean call = block("11", spec, " ", opCode, "github1", "@21");
        call.nextBlock = 12;
        blocks.add(call);
        BlockBean name = new BlockBean("21", "userName", "s", "", "getVar");
        blocks.add(name);
        blocks.add(block("12", "Toast %s", " ", "doToast", toast));
        return blocks;
    }

    private static BlockBean block(String id, String spec, String type, String opCode, String... parameters) {
        BlockBean b = new BlockBean(id, spec, type, "", opCode);
        java.util.Collections.addAll(b.parameters, parameters);
        return b;
    }

    private static ViewBean view(eC project, String xml, String id, int type, String convert, String parent, int index) {
        ViewBean bean = new ViewBean(id, type);
        bean.convert = convert;
        bean.image.resName = "default_image";
        bean.parent = parent == null ? "root" : parent;
        bean.parentType = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
        bean.index = index;
        project.a(xml, bean);
        return bean;
    }
}
