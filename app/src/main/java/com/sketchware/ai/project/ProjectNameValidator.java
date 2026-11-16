package com.sketchware.ai.project;

import android.content.Context;

import com.google.android.material.textfield.TextInputLayout;
import com.sketchware.remod.R;

import java.util.regex.Pattern;

import a.a.a.MB;
import a.a.a.xB;

/**
 * v2.0.5 (owner's 16 Nov phone report: "You can use maximum 20 letters" blocked saving a new version code / name of
 * the project "Sri_D_Varahi_Parking_Stand"): the project name's check in Project Settings and New Project, as upstream's
 * precompiled {@code a.a.a.VB} does it (not empty, a letter first, then letters, digits and "_"), with up to
 * {@link #MAX} characters instead of 20. VB itself stays for the keystore screen.
 */
public class ProjectNameValidator extends MB {

    public static final int MAX = 64;
    private static final Pattern NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*");

    public ProjectNameValidator(Context context, TextInputLayout layout) {
        super(context, layout);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        String text = s.toString().trim();
        String error = errorOf(text, s);
        if (error == null) {
            b.setErrorEnabled(false);
            d = true;
        } else {
            b.setErrorEnabled(true);
            b.setError(error);
            d = false;
        }
    }

    private String errorOf(String trimmed, CharSequence s) {
        if (trimmed.length() <= 0) return xB.b().a(a, R.string.invalid_value_min_lenth, 1);
        if (trimmed.length() > MAX) return xB.b().a(a, R.string.invalid_value_max_lenth, MAX);
        if (!Character.isLetter(s.charAt(0))) return xB.b().a(a, R.string.logic_editor_message_variable_name_must_start_letter);
        if (!NAME.matcher(s.toString()).matches()) return xB.b().a(a, R.string.invalid_value_rule_3);
        return null;
    }

    /** The same rule without a field (tests, other screens). */
    public static boolean valid(String name) {
        if (name == null) return false;
        String t = name.trim();
        return !t.isEmpty() && t.length() <= MAX && Character.isLetter(name.charAt(0)) && NAME.matcher(name).matches();
    }
}
