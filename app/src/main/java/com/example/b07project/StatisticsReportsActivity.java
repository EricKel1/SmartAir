package com.example.b07project;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.example.b07project.fragments.ReportsFragment;
import com.example.b07project.fragments.StatisticsFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class StatisticsReportsActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics_reports);

        initializeViews();
        setupViewPager();

        BackToParent bh = new BackToParent();
        findViewById(R.id.btnBack3).setOnClickListener(v -> finish());

        //To move the top elements under the phone's nav bar so buttons and whatnot
        //can be pressed
        TopMover mover = new TopMover(this);
        mover.adjustTopColored("#2596be");
    }

    private void initializeViews() {
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
    }

    private void setupViewPager() {
        String childId = getIntent().getStringExtra("EXTRA_CHILD_ID");
        boolean readOnly = getIntent().getBooleanExtra("EXTRA_READ_ONLY", false);
        ViewPagerAdapter adapter = new ViewPagerAdapter(this, childId, readOnly);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Statistics");
                    break;
                case 1:
                    tab.setText("Reports");
                    break;
            }
        }).attach();
    }

    private static class ViewPagerAdapter extends FragmentStateAdapter {
        private final String childId;
        private final boolean readOnly;

        public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity, String childId, boolean readOnly) {
            super(fragmentActivity);
            this.childId = childId;
            this.readOnly = readOnly;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return StatisticsFragment.newInstance(childId);
                case 1:
                    return ReportsFragment.newInstance(childId, readOnly);
                default:
                    return StatisticsFragment.newInstance(childId);
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
