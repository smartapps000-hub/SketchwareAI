package h;

/** From the IDE's palette icons (Icon*.getBean): type, convert, inject, width, height. */
final class IconDefaults {
    static final Object[][] ROWS = {
        // com/besome/sketch/editor/view/palette/Icon*.getBean (upstream's own palette): convert = the widget's class
        // name. v1.8.5 fixtures mirror it (earlier fixtures left convert empty for these, unlike a real project).
        {0, "LinearLayout", "", 0, 0}, {2, "HorizontalScrollView", "", 0, 0}, {3, "Button", "", 0, 0},
        {4, "TextView", "", 0, 0}, {5, "EditText", "", 0, 0}, {6, "ImageView", "", 0, 0}, {7, "WebView", "", 0, 0},
        {8, "ProgressBar", "", 0, 0}, {9, "ListView", "", 0, 0}, {10, "Spinner", "", 0, 0}, {11, "CheckBox", "", 0, 0},
        {12, "ScrollView", "", 0, 0}, {13, "Switch", "", 0, 0}, {14, "SeekBar", "", 0, 0},
        {15, "CalendarView", "", 0, 0}, {17, "com.google.android.gms.ads.AdView", "", 0, 0},
        {18, "com.google.android.gms.maps.MapView", "", 0, 0},
        {33, "com.allenliu.badgeview.BadgeView", "", -2, -2}, // IconBadgeView.java
        {32, "com.google.android.material.bottomnavigation.BottomNavigationView", "", -1, 0}, // IconBottomNavigationView.java
        {36, "androidx.cardview.widget.CardView", "app:cardElevation=\"4dp\"\napp:cardCornerRadius=\"12dp\"", -1, 0}, // IconCardView.java
        {43, "de.hdodenhof.circleimageview.CircleImageView", "app:civ_border_width=\"3dp\"\napp:civ_border_color=\"#008DCD\"\napp:civ_circle_background_color=\"#FFFFFF\"\napp:civ_border_overlay=\"true\"", 0, 0}, // IconCircleImageView.java
        {47, "br.tiagohm.codeview.CodeView", "", -1, -2}, // IconCodeView.java
        {37, "com.google.android.material.appbar.CollapsingToolbarLayout", "android:fitsSystemWindows=\"true\"\napp:layout_scrollFlags=\"scroll|exitUntilCollapsed\"", -1, 0}, // IconCollapsingToolbar.java
        {42, "com.google.android.gms.common.SignInButton", "", -2, 0}, // IconGoogleSignInButton.java
        {44, "com.airbnb.lottie.LottieAnimationView", "android:scaleType=\"fitCenter\"\nandroid:layout_centerInParent=\"true\"\napp:lottie_fileName=\"your_file.json\"\napp:lottie_loop=\"true\" \napp:lottie_autoPlay=\"true\"", -2, 0}, // IconLottieAnimation.java
        {41, "com.google.android.material.button.MaterialButton", "android:textAppearance=\"@style/TextAppearance.MaterialComponents.Button\"\napp:backgroundTint=\"@color/colorPrimary\"\napp:cornerRadius=\"8dp\"\nstyle=\"@style/Widget.MaterialComponents.Button\"", -2, 0}, // IconMaterialButton.java
        {46, "affan.ahmad.otp.OTPView", "", -1, 0}, // IconOTPView.java
        {34, "com.andrognito.patternlockview.PatternLockView", "", -1, 0}, // IconPatternLockView.java
        {40, "RadioGroup", "", -1, 0}, // IconRadioGroup.java
        {48, "androidx.recyclerview.widget.RecyclerView", "", -1, -2}, // IconRecyclerView.java
        {39, "androidx.swiperefreshlayout.widget.SwipeRefreshLayout", "", -1, -1}, // IconSwipeRefreshLayout.java
        {30, "com.google.android.material.tabs.TabLayout", "app:tabGravity=\"fill\"\napp:tabMode=\"fixed\"\napp:tabIndicatorHeight=\"3dp\"\napp:tabIndicatorColor=\"@android:color/white\"\napp:tabSelectedTextColor=\"@android:color/white\"\napp:tabTextColor=\"@android:color/white\"\napp:tabTextAppearance=\"@android:style/TextAppearance.Widget.TabWidget\"", -1, 0}, // IconTabLayout.java
        {38, "com.google.android.material.textfield.TextInputLayout", "style=\"@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox\"", -1, 0}, // IconTextInputLayout.java
        {31, "androidx.viewpager.widget.ViewPager", "", -1, -1}, // IconViewPager.java
        {35, "com.sayuti.lib.WaveSideBar", "app:sidebar_text_alignment=\"center\"\napp:sidebar_position=\"right\"\napp:sidebar_max_offset=\"80dp\"", -2, -1}, // IconWaveSideBar.java
        {45, "com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView", "app:autoPlay=\"false\"\napp:useWebUi=\"false\"\napp:showYouTubeButton=\"true\"\napp:showFullScreenButton=\"true\"\napp:showVideoCurrentTime=\"true\"\napp:showVideoDuration=\"true\"\napp:showSeekBar=\"true\"\napp:handleNetworkEvents=\"false\"\napp:enableAutomaticInitialization=\"true\"", -1, -2}, // IconYoutubePlayer.java
    };
    /** IconMaterialButton.getBean() in a Material 3 project (v1.8.4: no explicit style, the theme's button). */
    static final String M3_MATERIALBUTTON = "app:backgroundTint=\"@color/colorPrimary\"";
    /** IconTextInputLayout.getBean() in a Material 3 project (v1.8.4: the theme's outlined style). */
    static final String M3_TEXTINPUTLAYOUT = "style=\"?attr/textInputOutlinedStyle\"";
}
