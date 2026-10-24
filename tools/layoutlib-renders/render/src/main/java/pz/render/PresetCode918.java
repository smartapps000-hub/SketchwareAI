package pz.render;

/** Generated: initializeLogic() of fixture 918 (import_generated.py). */
public final class PresetCode918 {
    public static void run(androidx.viewpager.widget.ViewPager viewpager1, com.google.android.material.tabs.TabLayout tablayout1) {

		final String[] pageTitles = {"First", "Second", "Third"};
		viewpager1.setAdapter(new androidx.viewpager.widget.PagerAdapter() {
			    @Override
			    public int getCount() {
				        return pageTitles.length;
				    }
			
			    @Override
			    public boolean isViewFromObject(android.view.View view, Object object) {
				        return view == object;
				    }
			
			    @Override
			    public Object instantiateItem(android.view.ViewGroup container, int position) {
				        android.widget.TextView page = new android.widget.TextView(container.getContext());
				        page.setText(pageTitles[position] + " page");
				        page.setTextSize(20);
				        page.setGravity(android.view.Gravity.CENTER);
				        container.addView(page);
				        return page;
				    }
			
			    @Override
			    public void destroyItem(android.view.ViewGroup container, int position, Object object) {
				        container.removeView((android.view.View) object);
				    }
			
			    @Override
			    public CharSequence getPageTitle(int position) {
				        return pageTitles[position];
				    }
		});
		tablayout1.setupWithViewPager(viewpager1);
	
    }
}
