package dev.aldi.sayuti.editor.view.palette;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.palette.IconBase;
import com.sketchware.remod.R;

import mod.agus.jcoderz.beans.ViewBeans;

public class IconMaterialButton extends IconBase {

    public IconMaterialButton(Context context) {
        super(context);
    }

    @Override
    public void a(Context context) {
        super.a(context);
        setWidgetImage(R.drawable.widget_material_button);
        setWidgetName("MaterialButton");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON;
        viewBean.layout.orientation = VERTICAL;
        viewBean.layout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewBean.layout.gravity = Gravity.CENTER;
        viewBean.layout.paddingLeft = 8;
        viewBean.layout.paddingTop = 8;
        viewBean.layout.paddingRight = 8;
        viewBean.layout.paddingBottom = 8;
        viewBean.text.textColor = Color.WHITE;
        viewBean.text.text = getName();
        viewBean.convert = "com.google.android.material.button.MaterialButton";
        if (com.sketchware.ai.editor.DesignTheme.material3()) {
            // Material 3 project: the theme's button style (Widget.Material3.Button under the M3 AppTheme: full-pill
            // shape, M3 type) in the project's primary colour. No explicit M3 style: it would fail to inflate if the
            // project is switched back to the MaterialComponents theme later.
            viewBean.inject = "app:backgroundTint=\"@color/colorPrimary\"";
        } else {
            viewBean.inject = "android:textAppearance=\"@style/TextAppearance.MaterialComponents.Button\"\napp:backgroundTint=\"@color/colorPrimary\"\napp:cornerRadius=\"8dp\"\nstyle=\"@style/Widget.MaterialComponents.Button\"";
        }
        return viewBean;
    }
}
