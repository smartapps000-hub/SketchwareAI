package pz.render;

/** Generated: initializeLogic() of fixture 916 (import_generated.py). */
public final class PresetCode916 {
    public static void run(com.google.android.material.bottomnavigation.BottomNavigationView bottomnavigation1, android.widget.TextView textview1) {

		bottomnavigation1.getMenu().add(0, 0, 0, "Home").setIcon(android.R.drawable.ic_menu_view);
		bottomnavigation1.getMenu().add(0, 1, 1, "Search").setIcon(android.R.drawable.ic_menu_search);
		bottomnavigation1.getMenu().add(0, 2, 2, "Profile").setIcon(android.R.drawable.ic_menu_myplaces);
		bottomnavigation1.setOnItemSelectedListener(new com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener() {
			    @Override
			    public boolean onNavigationItemSelected(android.view.MenuItem item) {
				        textview1.setText(item.getTitle());
				        return true;
				    }
		});
	
    }
}
