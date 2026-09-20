package com.sketchware.ai.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.sketchware.remod.R;

/**
 * Dark liquid glass for legacy screens that were still white or classic (Library Manager and
 * its Firebase / AdMob / Google Maps / AppCompat pages, Export Project, Local library manager):
 * obsidian window and toolbar, dark cards, amber pill buttons, lightened text. Re-applied on
 * every layout so rows added later are converted too; each view is converted once.
 */
public final class GlassScreens {

    static final String[] SCREENS = {
            "com.besome.sketch.editor.manage.library.ManageLibraryActivity",
            "com.besome.sketch.editor.manage.library.admob.AdmobActivity",
            "com.besome.sketch.editor.manage.library.admob.ManageAdmobActivity",
            "com.besome.sketch.editor.manage.library.firebase.FirebaseActivity",
            "com.besome.sketch.editor.manage.library.firebase.ManageFirebaseActivity",
            "com.besome.sketch.editor.manage.library.googlemap.ManageGoogleMapActivity",
            "com.besome.sketch.editor.manage.library.compat.ManageCompatActivity",
            "com.besome.sketch.export.ExportProjectActivity",
            "dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity",
            "com.besome.sketch.editor.manage.font.ManageFontActivity",
            "com.besome.sketch.editor.manage.image.ManageImageActivity",
            "com.besome.sketch.editor.manage.sound.ManageSoundActivity",
            "com.besome.sketch.editor.manage.view.ManageViewActivity",
            "mod.agus.jcoderz.editor.manage.permission.ManagePermissionActivity",
            "mod.agus.jcoderz.editor.manage.resource.ManageResourceActivity",
            "mod.hey.studios.activity.managers.assets.ManageAssetsActivity",
            "mod.hey.studios.activity.managers.java.ManageJavaActivity",
            "mod.hey.studios.activity.managers.nativelib.ManageNativelibsActivity",
            "dev.aldi.sayuti.editor.manage.ManageCustomAttributeActivity",
    };

    private static final int CARD = 0xFF1E1E22;

    private GlassScreens() {
    }

    static boolean handles(String name) {
        for (String s : SCREENS) if (s.equals(name)) return true;
        return false;
    }

    static void apply(Activity activity) {
        ObsidianWindow.apply(activity);
        View toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setBackgroundResource(R.drawable.ob_toolbar_glass);
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        Runnable pass = () -> {
            convert(content);
            ObsidianAdapter.adapt(content);
        };
        pass.run();
        content.getViewTreeObserver().addOnGlobalLayoutListener(pass::run);
    }

    private static void convert(View view) {
        if (view.getTag(R.id.ob_glassed) == null) {
            if (view instanceof CardView) {
                view.setTag(R.id.ob_glassed, Boolean.TRUE);
                CardView card = (CardView) view;
                card.setCardBackgroundColor(CARD);
                card.setCardElevation(0f);
                card.setRadius(16 * view.getResources().getDisplayMetrics().density);
            } else if (view instanceof Button && isClassicButton((Button) view)) {
                view.setTag(R.id.ob_glassed, Boolean.TRUE);
                Button button = (Button) view;
                float d = view.getResources().getDisplayMetrics().density;
                button.setBackgroundTintList(null);
                button.setBackgroundResource(R.drawable.ob_safe_cta_state);
                button.setTextColor(Color.WHITE);
                button.setAllCaps(false);
                button.setStateListAnimator(null);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setMinHeight((int) (40 * d));
                button.setMinimumHeight((int) (40 * d));
                button.setPadding((int) (20 * d), 0, (int) (20 * d), 0);
                button.setSingleLine(true);
            } else if (isListRow(view)) {
                // Every list row becomes a slim glass pill (inset, so rows don't touch),
                // keeping an amber touch ripple.
                view.setTag(R.id.ob_glassed, Boolean.TRUE);
                float d = view.getResources().getDisplayMetrics().density;
                android.graphics.drawable.Drawable tile = view.getContext().getDrawable(R.drawable.ob_tile);
                android.graphics.drawable.InsetDrawable pill = new android.graphics.drawable.InsetDrawable(tile,
                        (int) (6 * d), (int) (3 * d), (int) (6 * d), (int) (3 * d));
                view.setBackground(new android.graphics.drawable.RippleDrawable(
                        ColorStateList.valueOf(0x33FF7A29), pill, pill));
            } else if (view instanceof android.widget.ListView) {
                view.setTag(R.id.ob_glassed, Boolean.TRUE);
                ((android.widget.ListView) view).setDivider(null);
                ((android.widget.ListView) view).setDividerHeight(0);
            } else if (view.getId() == R.id.toolbar) {
                view.setTag(R.id.ob_glassed, Boolean.TRUE);
                view.setBackgroundResource(R.drawable.ob_toolbar_glass);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) convert(group.getChildAt(i));
        }
    }

    /** A row of a ListView/GridView/RecyclerView that isn't already a card. */
    private static boolean isListRow(View view) {
        Object parent = view.getParent();
        if (!(parent instanceof android.widget.AbsListView || parent instanceof androidx.recyclerview.widget.RecyclerView)) {
            return false;
        }
        return !(view instanceof CardView) && !(view instanceof ViewGroup && ((ViewGroup) view).getChildCount() == 1
                && ((ViewGroup) view).getChildAt(0) instanceof CardView);
    }

    /** Filled Material/AppCompat buttons (flat blue, indigo, red); text-only buttons are kept. */
    private static boolean isClassicButton(Button button) {
        if (button instanceof android.widget.CompoundButton) return false;
        ColorStateList tint = button.getBackgroundTintList();
        if (tint != null) return Color.alpha(tint.getDefaultColor()) > 200;
        return button.getBackground() != null && !(button.getBackground() instanceof GlassDrawable);
    }
}
