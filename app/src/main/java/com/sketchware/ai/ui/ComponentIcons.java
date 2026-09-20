package com.sketchware.ai.ui;

import com.besome.sketch.beans.ComponentBean;
import com.sketchware.remod.R;

/**
 * Amber line icons for the Add Component grid (Obsidian liquid glass). Component types added by
 * users (Components Maker) keep their own icon.
 */
public final class ComponentIcons {

    private ComponentIcons() {
    }

    public static int forType(int type) {
        switch (type) {
            case 1: return R.drawable.ob_c_intent;
            case 2: return R.drawable.ob_c_sharedpref;
            case 3: return R.drawable.ob_c_calendar;
            case 4: return R.drawable.ob_c_vibrator;
            case 5: return R.drawable.ob_c_timer;
            case 6: return R.drawable.ob_c_firebase_db;
            case 7: return R.drawable.ob_c_dialog;
            case 8: return R.drawable.ob_c_mediaplayer;
            case 9: return R.drawable.ob_c_soundpool;
            case 10: return R.drawable.ob_c_objectanimator;
            case 11: return R.drawable.ob_c_gyroscope;
            case 12: return R.drawable.ob_c_firebase_auth;
            case 13: return R.drawable.ob_c_interstitial_ad;
            case 14: return R.drawable.ob_c_firebase_storage;
            case 15: return R.drawable.ob_c_camera;
            case 16: return R.drawable.ob_c_file_picker;
            case 17: return R.drawable.ob_c_request_network;
            case 18: return R.drawable.ob_c_text_to_speech;
            case 19: return R.drawable.ob_c_speech_to_text;
            case 20: return R.drawable.ob_c_bluetooth;
            case 21: return R.drawable.ob_c_location;
            case 22: return R.drawable.ob_c_rewarded_video;
            case 23: return R.drawable.ob_c_progress_dialog;
            case 24: return R.drawable.ob_c_date_picker;
            case 25: return R.drawable.ob_c_time_picker;
            case 26: return R.drawable.ob_c_notification;
            case 27: return R.drawable.ob_c_fragment_adapter;
            case 28: return R.drawable.ob_c_phone_auth;
            case 29: return R.drawable.ob_c_dynamic_links;
            case 30: return R.drawable.ob_c_cloud_message;
            case 31: return R.drawable.ob_c_google_login;
            case 32: return R.drawable.ob_c_onesignal;
            case 33: return R.drawable.ob_c_fb_ads_banner;
            case 34: return R.drawable.ob_c_fb_ads_interstitial;
            default: return ComponentBean.getIconResource(type);
        }
    }

    /**
     * Line icon for a view type (event lists, Add Event): the same icons as the editor palette.
     * Custom view types keep their original icon.
     */
    public static int forView(int viewType) {
        switch (viewType) {
            case 0: return R.drawable.ob_w_linear_horizontal;
            case 1: return R.drawable.ob_w_linear_vertical;
            case 2: return R.drawable.ob_w_scroll_view_horizontal;
            case 3: return R.drawable.ob_w_button;
            case 4: return R.drawable.ob_w_text_view;
            case 5: return R.drawable.ob_w_edit_text;
            case 6: return R.drawable.ob_w_image_view;
            case 7: return R.drawable.ob_w_web_view;
            case 8: return R.drawable.ob_w_progress_bar;
            case 9: return R.drawable.ob_w_list_view;
            case 10: return R.drawable.ob_w_spinner;
            case 11: return R.drawable.ob_w_check_box;
            case 12: return R.drawable.ob_w_scroll_view_vertical;
            case 13: return R.drawable.ob_w_switch;
            case 14: return R.drawable.ob_w_seek_bar;
            case 15: return R.drawable.ob_w_calendar_view;
            case 16: return R.drawable.ob_w_material_button;
            case 17: return R.drawable.ob_w_ad_view;
            case 18: return R.drawable.ob_w_map_view;
            case 19: return R.drawable.ob_w_radio_button;
            case 20: return R.drawable.ob_w_rating_bar;
            case 21: return R.drawable.ob_w_video_view;
            case 22: return R.drawable.ob_w_search_view;
            case 23: return R.drawable.ob_w_auto_complete_text_view;
            case 24: return R.drawable.ob_w_multi_auto_complete_text_view;
            case 25: return R.drawable.ob_w_grid_view;
            case 26: return R.drawable.ob_w_analog_clock;
            case 27: return R.drawable.ob_w_date_picker;
            case 28: return R.drawable.ob_w_time_picker;
            case 29: return R.drawable.ob_w_digital_clock;
            case 30: return R.drawable.ob_w_tab_layout;
            case 31: return R.drawable.ob_w_view_pager;
            case 32: return R.drawable.ob_w_bottom_navigation_view;
            case 33: return R.drawable.ob_w_badge_view;
            case 34: return R.drawable.ob_w_pattern_lock_view;
            case 35: return R.drawable.ob_w_wave_side_bar;
            case 36: return R.drawable.ob_w_card_view;
            case 37: return R.drawable.ob_w_collapsing_toolbar;
            case 38: return R.drawable.ob_w_text_input_layout;
            case 39: return R.drawable.ob_w_swipe_refresh_layout;
            case 40: return R.drawable.ob_w_radio_group;
            case 41: return R.drawable.ob_w_material_button;
            case 42: return R.drawable.ob_w_google_sign_in_button;
            case 43: return R.drawable.ob_w_circle_image_view;
            case 44: return R.drawable.ob_w_lottie_animation;
            case 45: return R.drawable.ob_w_youtube_player;
            case 46: return R.drawable.ob_w_o_t_p_view;
            case 47: return R.drawable.ob_w_code_view;
            case 48: return R.drawable.ob_w_recycler_view;
            default: return com.besome.sketch.beans.ViewBean.getViewTypeResId(viewType);
        }
    }

    /**
     * Grid label for a narrow cell: long names are split onto two lines between words
     * ("Shared" / "Preferences", "Firebase" / "Storage"), never mid-word; the cell's auto-size
     * then shrinks the text until the longer word fits.
     */
    public static String twoLineLabel(String name) {
        if (name == null || name.length() <= 9 || name.indexOf('\n') >= 0) return name;
        java.util.List<Integer> cuts = new java.util.ArrayList<>();
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i), p = name.charAt(i - 1);
            if (c == ' ' || Character.isUpperCase(c) && Character.isLowerCase(p)) cuts.add(i);
        }
        if (cuts.isEmpty()) return name;
        int best = cuts.get(0);
        for (int cut : cuts) {
            if (Math.abs(cut * 2 - name.length()) < Math.abs(best * 2 - name.length())) best = cut;
        }
        String first = name.substring(0, best).trim(), second = name.substring(best).trim();
        return first + "\n" + second;
    }
}
