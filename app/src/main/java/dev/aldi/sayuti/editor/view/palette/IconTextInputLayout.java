package dev.aldi.sayuti.editor.view.palette;

import android.content.Context;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.palette.IconBase;
import com.sketchware.remod.R;

import mod.agus.jcoderz.beans.ViewBeans;

public class IconTextInputLayout extends IconBase {

    public IconTextInputLayout(Context context) {
        super(context);
    }

    @Override
    public void a(Context context) {
        super.a(context);
        setWidgetImage(R.drawable.widget_edit_text);
        setWidgetName("TextInputLayout");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT;
        viewBean.layout.orientation = VERTICAL;
        viewBean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
        viewBean.layout.paddingLeft = 0;
        viewBean.layout.paddingTop = 0;
        viewBean.layout.paddingRight = 0;
        viewBean.layout.paddingBottom = 0;
        viewBean.convert = "com.google.android.material.textfield.TextInputLayout";
        // Material 3 project: the theme's outlined style (Widget.Material3.TextInputLayout.OutlinedBox under the M3
        // AppTheme). A theme attribute, not the M3 style itself: M3 styles need M3 theme colours (colorOutline ...) and
        // fail to inflate if the project is switched back to the MaterialComponents theme later.
        viewBean.inject = com.sketchware.ai.editor.DesignTheme.material3()
                ? "style=\"?attr/textInputOutlinedStyle\""
                : "style=\"@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox\"";
        return viewBean;
    }
}
