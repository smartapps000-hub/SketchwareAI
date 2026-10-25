package com.sketchware.ai.project;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.TextBean;
import com.besome.sketch.beans.ViewBean;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.kC;

/**
 * Templates of the New Project screen (v1.8.7). Each one writes ordinary project content: views on main.xml (and the
 * drawer layout), the activity's toolbar / FAB / drawer options, and where a widget needs code to work (the bottom
 * navigation's menu, the pager's pages) one "add source directly" block in onCreate. The user sees and edits all of it
 * in the View and Event tabs; nothing is hidden in generated code.
 * <p>
 * Written to the project's data folder ({@code /.sketchware/data/<sc_id>}) right after the project is created, with
 * the project managers' own load and save methods ({@code hC}, {@code eC}); the design editor then opens it as any
 * project. The generated Java of the blocks is plain Java 7 (anonymous classes, no lambdas), so it compiles at every
 * Java level of the Build Settings.
 */
public final class ProjectPresets {

    public static final String EMPTY = "empty";
    public static final String SCAFFOLD = "scaffold";
    public static final String BOTTOM_NAVIGATION = "bottom_navigation";
    public static final String DRAWER = "drawer";
    public static final String TABS = "tabs";

    /** id, badge, title, description: the order of the screen's carousel. */
    public static final String[][] LIST = {
            {EMPTY, "Standard", "Empty Activity", "Toolbar and an empty screen"},
            {SCAFFOLD, "Material 3", "Material 3 Scaffold", "Toolbar, elevated cards and a floating action button"},
            {BOTTOM_NAVIGATION, "Navigation", "Bottom Navigation", "Three-item navigation bar that switches the content"},
            {DRAWER, "Classic", "Navigation Drawer", "Slide-out drawer with a header and menu rows"},
            {TABS, "Tabs", "Tabbed ViewPager", "Tabs under the toolbar with three swipeable pages"},
    };

    private static final int LINEAR = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
    private static final int VSCROLL = ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW;
    private static final int TEXTVIEW = ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW;
    private static final int TABLAYOUT = 30;
    private static final int VIEWPAGER = 31;
    private static final int BOTTOMNAVIGATION = 32;
    private static final int CARDVIEW = 36;
    /** Project image name of the scaffold's FAB icon. */
    static final String FAB_IMAGE = "fab_add";

    /** Code of the bottom navigation template (one add-source block in onCreate). */
    static final String BOTTOM_NAVIGATION_CODE =
            "bottomnavigation1.getMenu().add(0, 0, 0, \"Home\").setIcon(android.R.drawable.ic_menu_view);\n"
                    + "bottomnavigation1.getMenu().add(0, 1, 1, \"Search\").setIcon(android.R.drawable.ic_menu_search);\n"
                    + "bottomnavigation1.getMenu().add(0, 2, 2, \"Profile\").setIcon(android.R.drawable.ic_menu_myplaces);\n"
                    + "bottomnavigation1.setOnItemSelectedListener(new com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener() {\n"
                    + "    @Override\n"
                    + "    public boolean onNavigationItemSelected(android.view.MenuItem item) {\n"
                    + "        textview1.setText(item.getTitle());\n"
                    + "        return true;\n"
                    + "    }\n"
                    + "});";

    /** Code of the tabs template: three text pages in the ViewPager, the TabLayout follows it. */
    static final String TABS_CODE =
            "final String[] pageTitles = {\"First\", \"Second\", \"Third\"};\n"
                    + "viewpager1.setAdapter(new androidx.viewpager.widget.PagerAdapter() {\n"
                    + "    @Override\n"
                    + "    public int getCount() {\n"
                    + "        return pageTitles.length;\n"
                    + "    }\n"
                    + "\n"
                    + "    @Override\n"
                    + "    public boolean isViewFromObject(android.view.View view, Object object) {\n"
                    + "        return view == object;\n"
                    + "    }\n"
                    + "\n"
                    + "    @Override\n"
                    + "    public Object instantiateItem(android.view.ViewGroup container, int position) {\n"
                    + "        android.widget.TextView page = new android.widget.TextView(container.getContext());\n"
                    + "        page.setText(pageTitles[position] + \" page\");\n"
                    + "        page.setTextSize(20);\n"
                    + "        page.setGravity(android.view.Gravity.CENTER);\n"
                    + "        container.addView(page);\n"
                    + "        return page;\n"
                    + "    }\n"
                    + "\n"
                    + "    @Override\n"
                    + "    public void destroyItem(android.view.ViewGroup container, int position, Object object) {\n"
                    + "        container.removeView((android.view.View) object);\n"
                    + "    }\n"
                    + "\n"
                    + "    @Override\n"
                    + "    public CharSequence getPageTitle(int position) {\n"
                    + "        return pageTitles[position];\n"
                    + "    }\n"
                    + "});\n"
                    + "tablayout1.setupWithViewPager(viewpager1);";

    private ProjectPresets() {
    }

    public static boolean known(String preset) {
        for (String[] row : LIST) if (row[0].equals(preset)) return true;
        return false;
    }

    /**
     * Writes {@code preset} into project {@code scId}, which was just created (its data folder holds only the default
     * files). {@code appName} goes into the drawer header; {@code primary} colours the drawer header and the tab bar.
     */
    public static void write(String scId, String preset, String appName, int primary) {
        if (preset == null || EMPTY.equals(preset) || !known(preset)) return;

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
        int options = ProjectFileBean.OPTION_ACTIVITY_TOOLBAR;

        switch (preset) {
            case SCAFFOLD:
                options |= ProjectFileBean.OPTION_ACTIVITY_FAB;
                scaffold(project, xml);
                fabIcon(scId, project, xml);
                break;
            case BOTTOM_NAVIGATION:
                bottomNavigation(project, xml, java);
                break;
            case DRAWER:
                options |= ProjectFileBean.OPTION_ACTIVITY_DRAWER;
                // what the activity options dialog adds for "Drawer" (see the harness fixtures)
                files.c().add(new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_DRAWER, "_drawer_" + main.fileName));
                drawer(project, xml, main.getDrawerXmlName(), appName, primary);
                break;
            case TABS:
                tabs(project, xml, java, primary);
                break;
        }
        main.options = (main.options & ~ProjectFileBean.OPTION_ACTIVITY_MASK) | options;

        files.m();
        project.j();
    }

    private static void scaffold(eC project, String xml) {
        ViewBean scroll = view(project, xml, "vscroll1", VSCROLL, "ScrollView", null, 0, 0);
        scroll.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        scroll.layout.height = LayoutBean.LAYOUT_MATCH_PARENT;

        ViewBean column = view(project, xml, "linear1", LINEAR, "LinearLayout", "vscroll1", VSCROLL, 0);
        column.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
        column.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        padding(column, 16);

        ViewBean title = text(project, xml, "textview1", "linear1", 0, "Welcome", 22, true);
        title.layout.marginBottom = 4;
        text(project, xml, "textview2", "linear1", 1,
                "A toolbar, two elevated cards and a floating action button. Change them in the View tab.", 14, false);

        card(project, xml, "cardview1", 2, "linear2", "textview3", "textview4", "Card title",
                "Supporting text of the first card.");
        card(project, xml, "cardview2", 3, "linear3", "textview5", "textview6", "Second card",
                "Cards hold any layout: add buttons, images or lists.");
    }

    /**
     * The scaffold's floating action button gets a "+" icon: a project image (white 24dp plus at xhdpi, a Java resource
     * of the IDE, written to /.sketchware/resources/images/&lt;sc_id&gt;/ and listed in the project's resources like an
     * image the user imported). The theme's FAB style tints it (on-secondary). Without an image the FAB is an empty disc.
     */
    private static void fabIcon(String scId, eC project, String xml) {
        kC resources = new kC(scId);
        if (resources.q()) resources.r();
        else resources.s();
        File folder = new File(resources.e);
        File image = new File(folder, FAB_IMAGE + ".png");
        try (InputStream in = ProjectPresets.class.getResourceAsStream("preset_fab_add.png")) {
            if (in == null || (!folder.isDirectory() && !folder.mkdirs())) {
                android.util.Log.w("ProjectPresets", "FAB icon not written (" + (in == null ? "resource missing" : "no folder " + folder) + ")");
                return;
            }
            try (OutputStream out = new FileOutputStream(image)) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            android.util.Log.w("ProjectPresets", "FAB icon not written", e);
            return;
        }
        boolean listed = false;
        for (ProjectResourceBean bean : resources.b) {
            if (bean != null && FAB_IMAGE.equalsIgnoreCase(bean.resName)) listed = true;
        }
        if (!listed) {
            resources.b.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, FAB_IMAGE, image.getName()));
        }
        resources.x();
        project.h(xml).image.resName = FAB_IMAGE;
    }

    private static void card(eC project, String xml, String cardId, int index, String innerId, String titleId,
                             String bodyId, String title, String body) {
        ViewBean card = view(project, xml, cardId, CARDVIEW, "androidx.cardview.widget.CardView", "linear1", LINEAR, index);
        card.inject = "app:cardElevation=\"2dp\"\napp:cardCornerRadius=\"16dp\"";
        card.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        card.layout.marginTop = 16;

        ViewBean inner = view(project, xml, innerId, LINEAR, "LinearLayout", cardId, CARDVIEW, 0);
        inner.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
        inner.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        padding(inner, 16);
        text(project, xml, titleId, innerId, 0, title, 18, true);
        ViewBean bodyView = text(project, xml, bodyId, innerId, 1, body, 14, false);
        bodyView.layout.marginTop = 4;
    }

    private static void bottomNavigation(eC project, String xml, String java) {
        ViewBean root = view(project, xml, "linear1", LINEAR, "LinearLayout", null, 0, 0);
        root.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
        root.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        root.layout.height = LayoutBean.LAYOUT_MATCH_PARENT;

        ViewBean content = view(project, xml, "linear2", LINEAR, "LinearLayout", "linear1", LINEAR, 0);
        content.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
        content.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        content.layout.height = LayoutBean.LAYOUT_MATCH_PARENT;
        content.layout.weight = 1;
        content.layout.gravity = LayoutBean.GRAVITY_CENTER;

        text(project, xml, "textview1", "linear2", 0, "Home", 24, true);

        ViewBean navigation = view(project, xml, "bottomnavigation1", BOTTOMNAVIGATION,
                "com.google.android.material.bottomnavigation.BottomNavigationView", "linear1", LINEAR, 1);
        navigation.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;

        onCreate(project, java, BOTTOM_NAVIGATION_CODE);
    }

    private static void drawer(eC project, String xml, String drawerXml, String appName, int primary) {
        ViewBean column = view(project, xml, "linear1", LINEAR, "LinearLayout", null, 0, 0);
        column.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
        column.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        padding(column, 16);
        text(project, xml, "textview1", "linear1", 0, "Home", 22, true);
        ViewBean hint = text(project, xml, "textview2", "linear1", 1,
                "Open the drawer with the menu button or by swiping from the left edge.", 14, false);
        hint.layout.marginTop = 4;

        ViewBean header = view(project, drawerXml, "linear1", LINEAR, "LinearLayout", null, 0, 0);
        header.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
        header.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        header.layout.backgroundColor = primary | 0xFF000000;
        padding(header, 24);
        ViewBean name = text(project, drawerXml, "textview1", "linear1", 0,
                appName == null || appName.trim().isEmpty() ? "My app" : appName.trim(), 20, true);
        name.text.textColor = onColor(primary);

        String[] rows = {"Home", "Settings", "About"};
        for (int i = 0; i < rows.length; i++) {
            ViewBean row = view(project, drawerXml, "textview" + (i + 2), TEXTVIEW, "TextView", null, 0, i + 1);
            row.text.text = rows[i];
            row.text.textSize = 16;
            row.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
            padding(row, 16);
        }
    }

    private static void tabs(eC project, String xml, String java, int primary) {
        ViewBean root = view(project, xml, "linear1", LINEAR, "LinearLayout", null, 0, 0);
        root.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
        root.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        root.layout.height = LayoutBean.LAYOUT_MATCH_PARENT;

        ViewBean tabs = view(project, xml, "tablayout1", TABLAYOUT, "com.google.android.material.tabs.TabLayout",
                "linear1", LINEAR, 0);
        tabs.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        tabs.layout.backgroundColor = primary | 0xFF000000;
        // The palette's TabLayout attributes (IconTabLayout) with the text colour readable on the bar
        String on = (onColor(primary) == 0xFFFFFFFF) ? "@android:color/white" : "#FF1D1B20";
        tabs.inject = "app:tabGravity=\"fill\"\napp:tabMode=\"fixed\"\napp:tabIndicatorHeight=\"3dp\"\n"
                + "app:tabIndicatorColor=\"" + on + "\"\napp:tabSelectedTextColor=\"" + on + "\"\n"
                + "app:tabTextColor=\"" + on + "\"\napp:tabTextAppearance=\"@android:style/TextAppearance.Widget.TabWidget\"";

        ViewBean pager = view(project, xml, "viewpager1", VIEWPAGER, "androidx.viewpager.widget.ViewPager",
                "linear1", LINEAR, 1);
        pager.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        pager.layout.height = LayoutBean.LAYOUT_MATCH_PARENT;

        onCreate(project, java, TABS_CODE);
    }

    /** White or the M3 near-black on a background colour, whichever is readable (contrast 3:1, as M3Palette). */
    static int onColor(int background) {
        return M3Palette.contrast(0xFFFFFFFF, background | 0xFF000000) >= 3.0 ? 0xFFFFFFFF : 0xFF1D1B20;
    }

    /**
     * The parentType the editor itself stores when a view is dropped (ViewPane): 12 in a vertical ScrollView, 2 in a
     * horizontal one, 0 in everything drawn as a LinearLayout, cards and the other containers included. ViewPane gives a
     * child FrameLayout params for any other value (v1.8.7-v1.8.9 stored 36 for a card's column: the canvas crashed).
     */
    static int canvasParentType(int parentViewType) {
        if (parentViewType == VSCROLL) return VSCROLL;
        if (parentViewType == ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW) return ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW;
        return LINEAR;
    }

    private static ViewBean view(eC project, String xml, String id, int type, String convert, String parent,
                                 int parentType, int index) {
        ViewBean bean = new ViewBean(id, type);
        bean.convert = convert;
        bean.image.resName = "default_image";
        bean.parent = parent == null ? "root" : parent;
        bean.parentType = parent == null ? LINEAR : canvasParentType(parentType);
        bean.index = index;
        project.a(xml, bean);
        return bean;
    }

    private static ViewBean text(eC project, String xml, String id, String parent, int index, String value, int size,
                                 boolean bold) {
        ViewBean bean = view(project, xml, id, TEXTVIEW, "TextView", parent, LINEAR, index);
        bean.text.text = value;
        bean.text.textSize = size;
        if (bold) bean.text.textType = TextBean.TEXT_TYPE_BOLD;
        return bean;
    }

    private static void padding(ViewBean bean, int dp) {
        bean.layout.paddingLeft = dp;
        bean.layout.paddingTop = dp;
        bean.layout.paddingRight = dp;
        bean.layout.paddingBottom = dp;
    }

    private static void onCreate(eC project, String java, String code) {
        BlockBean block = new BlockBean("10", "add source directly %s.inputOnly", " ", "", "addSourceDirectly");
        block.color = 0xff5cb722;
        block.parameters.add(code);
        ArrayList<BlockBean> blocks = new ArrayList<>();
        blocks.add(block);
        project.a(java, "onCreate_initializeLogic", blocks);
    }
}
