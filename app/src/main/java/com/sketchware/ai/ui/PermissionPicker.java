package com.sketchware.ai.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v1.9.6: the permission slot's picker in the look of the logic editor's "Select widget" list (owner's 7 Nov
 * screenshots): a rounded dark container, a compact search box, rows with a radio bubble, a bold white name and a gray
 * subtitle. One tap picks the row and closes the picker; there are no Save / Cancel buttons (Back or a tap outside
 * closes it unchanged). Android classes only, so layoutlib can render {@link #content}.
 */
public final class PermissionPicker {

    public static final int SURFACE = 0xFF252528;
    public static final int NAME = 0xFFFFFFFF;
    public static final int SUBTITLE = 0xFFB0B0B0;
    public static final int HINT = 0xFF8E8E93;
    public static final int FIELD = 0xFF1C1C1F;
    public static final int FIELD_STROKE = 0xFF3A3A3E;
    public static final int ACCENT = 0xFFFF7A29;

    /** One row: the value the block gets, the bold name and the gray subtitle. */
    public static final class Item {
        public final String value;
        public final String name;
        public final String subtitle;

        public Item(String value, String name, String subtitle) {
            this.value = value;
            this.name = name;
            this.subtitle = subtitle;
        }
    }

    public interface Listener {
        void onPicked(String value);
    }

    private PermissionPicker() {
    }

    /** Shows the picker; {@code custom} makes a row for a typed name the list doesn't have (null: none). */
    public static Dialog show(Context context, String title, List<Item> items, String current, CustomRow custom,
                              Listener listener) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View content = content(context, title, items, current, custom, value -> {
            dialog.dismiss();
            listener.onPicked(value);
        });
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(context, 32), dp(context, 480));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        return dialog;
    }

    /** Builds the row a typed text gets when it isn't a listed value; null when the text can't be a permission. */
    public interface CustomRow {
        Item rowFor(String typed);
    }

    /** The picker's content: title, search box, list. */
    public static View content(Context context, String title, final List<Item> items, final String current,
                               final CustomRow custom, final Listener listener) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(SURFACE);
        background.setCornerRadius(dp(context, 16));
        root.setBackground(background);
        root.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 12));

        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(NAME);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(heading);

        final EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint("Search permissions");
        search.setHintTextColor(HINT);
        search.setTextColor(NAME);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        GradientDrawable field = new GradientDrawable();
        field.setColor(FIELD);
        field.setCornerRadius(dp(context, 12));
        field.setStroke(dp(context, 1), FIELD_STROKE);
        search.setBackground(field);
        search.setPadding(dp(context, 14), dp(context, 9), dp(context, 14), dp(context, 9));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = dp(context, 12);
        searchParams.bottomMargin = dp(context, 6);
        root.addView(search, searchParams);

        final ArrayList<Item> shown = new ArrayList<>(items);
        final Rows adapter = new Rows(context, shown, current);
        ListView list = new ListView(context);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0x14FFFFFF));
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shown.size()) listener.onPicked(shown.get(position).value);
        });
        // as tall as its rows, at most 420dp / 55% of the screen (a search with one hit leaves no empty box)
        int height = Math.min(dp(context, 420), context.getResources().getDisplayMetrics().heightPixels * 55 / 100);
        root.addView(new Capped(context, list, height), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String typed = s.toString().trim();
                String query = typed.toLowerCase(Locale.US);
                shown.clear();
                boolean listed = false;
                for (Item item : items) {
                    if (item.value.equalsIgnoreCase(typed)) listed = true;
                    if (query.isEmpty() || item.value.toLowerCase(Locale.US).contains(query)
                            || item.name.toLowerCase(Locale.US).contains(query)
                            || item.subtitle.toLowerCase(Locale.US).contains(query)) {
                        shown.add(item);
                    }
                }
                if (!typed.isEmpty() && !listed && custom != null) {
                    Item row = custom.rowFor(typed);
                    if (row != null) shown.add(0, row);
                }
                adapter.notifyDataSetChanged();
            }
        });
        return root;
    }

    private static final class Rows extends BaseAdapter {
        private final Context context;
        private final List<Item> items;
        private final String current;

        Rows(Context context, List<Item> items, String current) {
            this.context = context;
            this.items = items;
            this.current = current;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convertView;
            if (row == null) row = row(context);
            final Item item = items.get(position);
            RadioButton bubble = (RadioButton) row.getChildAt(0);
            LinearLayout texts = (LinearLayout) row.getChildAt(1);
            ((TextView) texts.getChildAt(0)).setText(item.name);
            TextView subtitle = (TextView) texts.getChildAt(1);
            subtitle.setText(item.subtitle);
            subtitle.setVisibility(TextUtils.isEmpty(item.subtitle) ? View.GONE : View.VISIBLE);
            bubble.setChecked(item.value.equals(current));
            return row;
        }
    }

    private static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 8), 0, dp(context, 8));
        row.setMinimumHeight(dp(context, 52));

        RadioButton bubble = new RadioButton(context);
        bubble.setClickable(false);
        bubble.setFocusable(false);
        bubble.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ACCENT, SUBTITLE}));
        bubble.setPadding(0, 0, 0, 0);
        bubble.setMinWidth(0);
        bubble.setMinimumWidth(0);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bubbleParams.rightMargin = dp(context, 8);
        row.addView(bubble, bubbleParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setTextColor(NAME);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        TextView subtitle = new TextView(context);
        subtitle.setTextColor(SUBTITLE);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        subtitle.setMaxLines(2);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        texts.addView(subtitle);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    /** Holds one child and caps its height (wrap content up to {@code maxHeight}). */
    static final class Capped extends LinearLayout {
        private final int maxHeight;

        Capped(Context context, View child, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
            addView(child, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            int limit = mode == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(size, maxHeight);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        }
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
