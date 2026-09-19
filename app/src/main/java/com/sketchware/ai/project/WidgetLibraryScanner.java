package com.sketchware.ai.project;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jq;
import mod.agus.jcoderz.beans.ViewBeans;

/**
 * Makes sure the libraries a layout's widgets need are part of the build.
 * <p>
 * Stock Sketchware only adds androidx/Material when "AppCompat and Design" is switched on in
 * the Library Manager. A layout can still contain a CardView (or a view converted to one, or
 * Inject code with app:card* attributes) with that switch off; AAPT2 then fails with
 * "attribute cardCornerRadius (aka &lt;package&gt;:cardCornerRadius) not found".
 * <ul>
 *     <li>CardView only needs androidx.cardview, which works with any theme, so just that
 *     library is added.</li>
 *     <li>Material/AppCompat-only widgets (TabLayout, BottomNavigationView, TextInputLayout,
 *     CollapsingToolbarLayout, MaterialButton, RecyclerView, ViewPager, SwipeRefreshLayout) need
 *     the AppCompat theme too, so AppCompat is switched on for the build — the same thing
 *     Sketchware already does when the Camera component is used.</li>
 * </ul>
 */
public final class WidgetLibraryScanner {

    private WidgetLibraryScanner() {
    }

    public static void scan(hC projectFileManager, eC projectDataManager, jq metadata) {
        ArrayList<ProjectFileBean> files = new ArrayList<>();
        try {
            if (projectFileManager.b() != null) files.addAll(projectFileManager.b());
            if (projectFileManager.c() != null) files.addAll(projectFileManager.c());
        } catch (RuntimeException ignored) {
        }
        for (ProjectFileBean file : files) {
            ArrayList<ViewBean> views;
            try {
                views = projectDataManager.d(file.getXmlName());
            } catch (RuntimeException e) {
                continue;
            }
            if (views == null) continue;
            for (ViewBean view : views) {
                check(view, metadata);
            }
        }
    }

    private static void check(ViewBean view, jq metadata) {
        String convert = view.convert == null ? "" : view.convert;
        String inject = view.inject == null ? "" : view.inject;

        if (view.type == ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW
                || convert.contains("androidx.cardview") || inject.contains("app:card")) {
            metadata.usesCardView = true;
        }
        switch (view.type) {
            case ViewBeans.VIEW_TYPE_LAYOUT_TABLAYOUT:
            case ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER:
            case ViewBeans.VIEW_TYPE_LAYOUT_BOTTOMNAVIGATIONVIEW:
            case ViewBeans.VIEW_TYPE_LAYOUT_COLLAPSINGTOOLBARLAYOUT:
            case ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT:
            case ViewBeans.VIEW_TYPE_LAYOUT_SWIPEREFRESHLAYOUT:
            case ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON:
            case ViewBeans.VIEW_TYPE_WIDGET_RECYCLERVIEW:
                metadata.g = true;
                break;
            default:
                if (convert.startsWith("com.google.android.material.")
                        || convert.startsWith("androidx.recyclerview.")
                        || convert.startsWith("androidx.swiperefreshlayout.")
                        || convert.startsWith("androidx.viewpager.")) {
                    metadata.g = true;
                }
        }
    }
}
