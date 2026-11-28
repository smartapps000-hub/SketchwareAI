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

import static com.sketchware.ai.project.GitHubSample.block;
import static com.sketchware.ai.project.GitHubSample.view;

/**
 * v2.1.6 (owner's directive "FINAL PUBLIC RELEASE … CROSS-PROJECT DEMO .SWB (JSON POWER PACK + GITHUB BLOCKS)"): the
 * "GitHub + JSON Live Demo", first in Restore → Demo. Made like the GitHub API Demo ({@link NewProjectSetup#create},
 * Empty Activity, Material 3), then filled with: a user-name field (octocat), "Load profile" and "Repository names"
 * buttons, a summary text, a dark code panel and a list; the GitHub component {@code github1}; the String variables
 * {@code userName}, {@code profile}, {@code summary}, the number {@code i} and the list {@code repoNames}. The blocks:
 * <ul>
 * <li>profile loaded (GitHub's own answer from api.github.com/users/&lt;name&gt;): <b>json get text</b> / <b>json get
 * number</b> read name, followers, public_repos and location; <b>json set</b> builds a new JSON object from them in
 * {@code profile} (nested "stats.followers"); the summary is read back from it and <b>json pretty</b> shows it in the
 * panel;</li>
 * <li>repositories listed (the component's list: name, stars, language …): a <b>repeat</b> over <b>json count</b> adds
 * each <b>json get text</b> at "[i].name" to {@code summary}'s "names[]" array (<b>json set</b> appends), <b>json array
 * to list</b> turns it into {@code repoNames} for the ListView, the first repository's stars come from <b>json get
 * number</b> at "[0].stars", and the panel shows <b>json pretty</b> of the built JSON;</li>
 * <li>error: GitHub's answer explained.</li>
 * </ul>
 * No token: public data only (GitHub allows 60 requests an hour per network without one).
 */
public final class JsonSample {

    public static final String NAME = "GitHub + JSON Live Demo";
    public static final String PROJECT_NAME = "GitHub_JSON_Live_Demo";
    public static final String PACKAGE = "com.my.githubjson";
    /** The demo's kind in its mark ({@link Demos}): first in the Demo tab. */
    public static final String KIND = "githubjson";
    public static final int DEMO_VERSION = 1;
    private static final String SEEDED = "github_json_sample_seeded";

    private JsonSample() {
    }

    /** True when a project of that name is in the list. */
    public static boolean exists() {
        for (HashMap<String, Object> project : lC.a()) {
            if (PROJECT_NAME.equals(yB.c(project, "my_ws_name"))) return true;
        }
        return false;
    }

    /** Once per install, like the GitHub API Demo (worker thread; storage access granted). Returns the id, or null. */
    public static synchronized String seedOnce(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("sketchware_ai_cloud_sync",
                Context.MODE_PRIVATE);
        if (prefs.getBoolean(SEEDED, false)) return null;
        prefs.edit().putBoolean(SEEDED, true).apply();
        if (exists()) return null;
        return create(context);
    }

    /** Makes the demo project (worker thread) and returns its id (picked under {@link ProjectIds#RESTORE_LOCK}). */
    public static String create(Context context) {
        synchronized (ProjectIds.RESTORE_LOCK) {
            return create(context, ProjectIds.free(lC.b()));
        }
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
            Demos.mark(scId, KIND, DEMO_VERSION);
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
        column.layout.height = LayoutBean.LAYOUT_MATCH_PARENT;
        column.layout.paddingLeft = 16;
        column.layout.paddingTop = 16;
        column.layout.paddingRight = 16;
        column.layout.paddingBottom = 16;
        ViewBean title = view(project, xml, "textview1", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView", "linear1", 0);
        title.text.text = NAME;
        title.text.textSize = 22;
        title.text.textType = TextBean.TEXT_TYPE_BOLD;
        title.text.textColor = 0xFFF4511E;
        ViewBean subtitle = view(project, xml, "textview3", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView", "linear1", 1);
        subtitle.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        subtitle.text.text = "Live JSON from api.github.com, read and built with the JSON blocks.";
        subtitle.text.textSize = 13;
        ViewBean field = view(project, xml, "edittext1", ViewBean.VIEW_TYPE_WIDGET_EDITTEXT, "EditText", "linear1", 2);
        field.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        field.layout.marginTop = 12;
        field.text.text = "octocat";
        field.text.hint = "GitHub user name";
        ViewBean profileButton = view(project, xml, "button1", ViewBean.VIEW_TYPE_WIDGET_BUTTON, "Button", "linear1", 3);
        profileButton.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        profileButton.layout.marginTop = 8;
        profileButton.layout.backgroundColor = 0xFFF4511E;
        profileButton.text.text = "Load profile (JSON text + number)";
        profileButton.text.textColor = 0xFFFFFFFF;
        ViewBean reposButton = view(project, xml, "button2", ViewBean.VIEW_TYPE_WIDGET_BUTTON, "Button", "linear1", 4);
        reposButton.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        reposButton.layout.backgroundColor = 0xFFF4511E;
        reposButton.text.text = "Repository names (JSON array to list)";
        reposButton.text.textColor = 0xFFFFFFFF;
        ViewBean output = view(project, xml, "textview2", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView", "linear1", 5);
        output.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        output.layout.marginTop = 12;
        output.text.text = "Tap a button: the app asks GitHub, reads the answer with the JSON blocks and builds new JSON.";
        output.text.textSize = 14;
        // the code panel: a dark card in either day or night mode, light text
        ViewBean panel = view(project, xml, "textview4", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView", "linear1", 6);
        panel.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        panel.layout.marginTop = 8;
        panel.layout.paddingLeft = 12;
        panel.layout.paddingTop = 10;
        panel.layout.paddingRight = 12;
        panel.layout.paddingBottom = 10;
        panel.layout.backgroundColor = 0xFF16181E;
        panel.text.text = "{ }";
        panel.text.textSize = 12;
        panel.text.textColor = 0xFFF5F5F7;
        ViewBean listView = view(project, xml, "listview1", ViewBean.VIEW_TYPE_WIDGET_LISTVIEW, "ListView", "linear1", 7);
        listView.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        listView.layout.height = LayoutBean.LAYOUT_MATCH_PARENT;
        listView.layout.marginTop = 8;

        project.h.computeIfAbsent(java, k -> new ArrayList<>()).add(new ComponentBean(GitHubCode.TYPE, "github1"));
        ArrayList<Pair<Integer, String>> variables = project.e.computeIfAbsent(java, k -> new ArrayList<>());
        variables.add(new Pair<>(2, "userName"));
        variables.add(new Pair<>(2, "profile"));
        variables.add(new Pair<>(2, "summary"));
        variables.add(new Pair<>(1, "i"));
        project.f.computeIfAbsent(java, k -> new ArrayList<>()).add(new Pair<>(2, "repoNames"));
        ArrayList<EventBean> events = project.i.computeIfAbsent(java, k -> new ArrayList<>());
        events.add(new EventBean(EventBean.EVENT_TYPE_VIEW, ViewBean.VIEW_TYPE_WIDGET_BUTTON, "button1", "onClick"));
        events.add(new EventBean(EventBean.EVENT_TYPE_VIEW, ViewBean.VIEW_TYPE_WIDGET_BUTTON, "button2", "onClick"));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_USER));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_REPOS));
        events.add(new EventBean(EventBean.EVENT_TYPE_COMPONENT, GitHubCode.TYPE, "github1", GitHubCode.EVENT_ERROR));

        project.a(java, "button1_onClick", ask("gitHubGetUser", "%m.GitHub get user %s.userOrEmptyForMe",
                "Asking GitHub for the profile…"));
        project.a(java, "button2_onClick", ask("gitHubListRepos", "%m.GitHub list repositories of %s.userOrEmptyForMine",
                "Asking GitHub for the repositories…"));
        project.a(java, "github1_" + GitHubCode.EVENT_USER, profileLoaded());
        project.a(java, "github1_" + GitHubCode.EVENT_REPOS, reposListed());

        ArrayList<BlockBean> error = new ArrayList<>();
        error.add(next(block("10", "%m.textview setText %s", " ", "setText", "textview2", "@20"), 11));
        error.add(block("20", "message", "s", "getArg"));
        error.add(block("11", "Toast %s", " ", "doToast", "@21"));
        error.add(block("21", "%s join %s", "s", "stringJoin", "GitHub: ", "@22"));
        error.add(block("22", "action", "s", "getArg"));
        project.a(java, "github1_" + GitHubCode.EVENT_ERROR, error);

        files.m();
        project.j();
    }

    /**
     * Profile loaded: a new JSON object built with the set blocks from GitHub's answer, the summary read back from it,
     * the object shown pretty in the panel.
     */
    static ArrayList<BlockBean> profileLoaded() {
        ArrayList<BlockBean> b = new ArrayList<>();
        // commands 10-19
        b.add(next(block("10", "set %m.varStr to %s", " ", "setVarString", "profile", ""), 11));
        b.add(next(json("11", "sketchJsonSetText", "profile", "login", "@30"), 12));
        b.add(next(json("12", "sketchJsonSetText", "profile", "name", "@31"), 13));
        b.add(next(json("13", "sketchJsonSetNumber", "profile", "stats.followers", "@33"), 14));
        b.add(next(json("14", "sketchJsonSetNumber", "profile", "stats.public_repos", "@35"), 15));
        b.add(next(json("15", "sketchJsonSetText", "profile", "location", "@37"), 16));
        b.add(next(block("16", "%m.textview setText %s", " ", "setText", "textview2", "@40"), 17));
        b.add(next(block("17", "%m.textview setText %s", " ", "setText", "textview4", "@60"), 18));
        b.add(block("18", "Toast %s", " ", "doToast", "@62"));
        // values read from GitHub's answer (the event's json)
        b.add(block("30", "login", "s", "getArg"));
        b.add(json("31", "sketchJsonGetText", "@32", "name"));
        b.add(block("32", "json", "s", "getArg"));
        b.add(json("33", "sketchJsonGetNumber", "@34", "followers"));
        b.add(block("34", "json", "s", "getArg"));
        b.add(json("35", "sketchJsonGetNumber", "@36", "public_repos"));
        b.add(block("36", "json", "s", "getArg"));
        b.add(json("37", "sketchJsonGetText", "@38", "location"));
        b.add(block("38", "json", "s", "getArg"));
        // the summary, read back from the built object: name · @login · followers · repositories
        b.add(block("40", "%s join %s", "s", "stringJoin", "@41", "@42"));
        b.add(json("41", "sketchJsonGetText", "@43", "name"));
        b.add(var("43", "profile", "s"));
        b.add(block("42", "%s join %s", "s", "stringJoin", "  ·  @", "@44"));
        b.add(block("44", "%s join %s", "s", "stringJoin", "@45", "@46"));
        b.add(json("45", "sketchJsonGetText", "@47", "login"));
        b.add(var("47", "profile", "s"));
        b.add(block("46", "%s join %s", "s", "stringJoin", "\\nFollowers: ", "@48"));
        b.add(block("48", "%s join %s", "s", "stringJoin", "@49", "@50"));
        b.add(block("49", "toString %d without decimal", "s", "toString", "@51"));
        b.add(json("51", "sketchJsonGetNumber", "@52", "stats.followers"));
        b.add(var("52", "profile", "s"));
        b.add(block("50", "%s join %s", "s", "stringJoin", "  ·  Public repositories: ", "@53"));
        b.add(block("53", "toString %d without decimal", "s", "toString", "@54"));
        b.add(json("54", "sketchJsonGetNumber", "@55", "stats.public_repos"));
        b.add(var("55", "profile", "s"));
        // the built object, pretty
        b.add(json("60", "sketchJsonPretty", "@61"));
        b.add(var("61", "profile", "s"));
        b.add(block("62", "%s join %s", "s", "stringJoin", "JSON built for @", "@63"));
        b.add(block("63", "login", "s", "getArg"));
        return b;
    }

    /**
     * Repositories listed: names collected into summary's "names[]" by a repeat over the count, the array turned into
     * the list for the ListView, the first repository's stars as a number.
     */
    static ArrayList<BlockBean> reposListed() {
        ArrayList<BlockBean> b = new ArrayList<>();
        // commands 10-29 (14 is the repeat; 15-16 inside it)
        b.add(next(block("10", "set %m.varStr to %s", " ", "setVarString", "summary", ""), 11));
        b.add(next(json("11", "sketchJsonSetText", "summary", "user", "@30"), 12));
        b.add(next(json("12", "sketchJsonSetNumber", "summary", "count", "@31"), 13));
        b.add(next(block("13", "set %m.varInt to %d", " ", "setVarInt", "i", "0"), 14));
        BlockBean repeat = next(block("14", "repeat %d", "c", "repeat", "@32"), 17);
        repeat.subStack1 = 15;
        b.add(repeat);
        b.add(next(json("15", "sketchJsonSetText", "summary", "names[]", "@34"), 16));
        b.add(block("16", "%m.varInt increase 1", " ", "increaseInt", "i"));
        b.add(next(json("17", "sketchJsonSetNumber", "summary", "first_stars", "@40"), 18));
        b.add(next(json("18", "sketchJsonToTextList", "@42", "names", "repoNames"), 19));
        b.add(next(block("19", "%m.listview setListViewData %m.listStr", " ", "listSetData", "listview1", "repoNames"), 20));
        b.add(next(block("20", "%m.textview setText %s", " ", "setText", "textview2", "@43"), 21));
        b.add(next(block("21", "%m.textview setText %s", " ", "setText", "textview4", "@47"), 22));
        b.add(block("22", "Toast %s", " ", "doToast", "@49"));
        // values
        b.add(block("30", "user", "s", "getArg"));
        b.add(block("31", "count", "d", "getArg"));
        b.add(json("32", "sketchJsonCount", "@33", ""));
        b.add(block("33", "reposJson", "s", "getArg"));
        // the i-th repository's name: JSON reposJson text at "[" + i + "].name"
        b.add(json("34", "sketchJsonGetText", "@35", "@36"));
        b.add(block("35", "reposJson", "s", "getArg"));
        b.add(block("36", "%s join %s", "s", "stringJoin", "[", "@37"));
        b.add(block("37", "%s join %s", "s", "stringJoin", "@38", "].name"));
        b.add(block("38", "toString %d without decimal", "s", "toString", "@39"));
        b.add(var("39", "i", "d"));
        b.add(json("40", "sketchJsonGetNumber", "@41", "[0].stars"));
        b.add(block("41", "reposJson", "s", "getArg"));
        b.add(var("42", "summary", "s"));
        b.add(block("43", "%s join %s", "s", "stringJoin", "@44", " repository names: JSON text at [i].name, then JSON array to list"));
        b.add(block("44", "toString %d without decimal", "s", "toString", "@45"));
        b.add(block("45", "length of %m.list", "d", "lengthList", "repoNames"));
        b.add(json("47", "sketchJsonPretty", "@48"));
        b.add(var("48", "summary", "s"));
        b.add(block("49", "%s join %s", "s", "stringJoin", "Stars of the newest repository: ", "@50"));
        b.add(block("50", "toString %d without decimal", "s", "toString", "@51"));
        b.add(json("51", "sketchJsonGetNumber", "@52", "first_stars"));
        b.add(var("52", "summary", "s"));
        return b;
    }

    /** set userName to edittext1's text → the GitHub block with userName → a Toast. */
    private static ArrayList<BlockBean> ask(String opCode, String spec, String toast) {
        ArrayList<BlockBean> blocks = new ArrayList<>();
        blocks.add(next(block("10", "set %m.varStr to %s", " ", "setVarString", "userName", "@20"), 11));
        blocks.add(block("20", "%m.textview getText", "s", "getText", "edittext1"));
        blocks.add(next(block("11", spec, " ", opCode, "github1", "@21"), 12));
        blocks.add(var("21", "userName", "s"));
        blocks.add(block("12", "Toast %s", " ", "doToast", toast));
        return blocks;
    }

    /** A JSON power pack block with its palette spec and type ({@link JsonCode#BLOCKS}). */
    private static BlockBean json(String id, String opCode, String... parameters) {
        for (String[] b : JsonCode.BLOCKS) {
            if (b[0].equals(opCode)) return block(id, b[3], b[1], opCode, parameters);
        }
        throw new IllegalArgumentException("no JSON block " + opCode);
    }

    private static BlockBean var(String id, String name, String type) {
        return new BlockBean(id, name, type, "", "getVar");
    }

    private static BlockBean next(BlockBean b, int next) {
        b.nextBlock = next;
        return b;
    }
}
