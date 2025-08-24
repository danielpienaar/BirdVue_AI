package com.varsitycollege.birdvue

import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.work.PeriodicWorkRequestBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.varsitycollege.birdvue.data.BirdInfoDatabase
import com.varsitycollege.birdvue.data.HomeViewModel
import com.varsitycollege.birdvue.databinding.ActivityHomeBinding
import com.varsitycollege.birdvue.ui.CommunityFragment
import com.varsitycollege.birdvue.ui.HotspotFragment
import com.varsitycollege.birdvue.ui.ObservationsFragment
import com.varsitycollege.birdvue.ui.SettingsFragment
import com.varsitycollege.birdvue.workers.ClearCacheWorker
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var model: HomeViewModel

    private fun setupPeriodicCacheClear() {
        val clearCacheRequest = PeriodicWorkRequestBuilder<ClearCacheWorker>(
            BirdInfoDatabase.CACHE_EXPIRY_DAYS.toLong(), // Use the constant from your DB
            TimeUnit.DAYS
        )
            // .setConstraints(Constraints.Builder().setRequiresCharging(true).build()) // Optional: Add constraints
            .build()

        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "clearBirdCacheWork",                 // Unique name for the work
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,      // Or .REPLACE if you need to update parameters
            clearCacheRequest
        )
        Log.d("BirdVue_AI", "Periodic cache clear worker scheduled.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPeriodicCacheClear()

        //Initialize viewmodel and bottom nav view
        model = ViewModelProvider(this)[HomeViewModel::class.java]
        bottomNavigationView = binding.bottomNavView

//        //Fix layout for insets
//        WindowCompat.setDecorFitsSystemWindows(window, false)
//
//        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            view.setPadding(0, systemBars.top, 0, 0) // push content away from bars
//            //binding.fabAdd.translationY = -systemBars.bottom / 2f
//
//            insets
//        }
//
//        // Force initial dispatch
//        ViewCompat.requestApplyInsets(binding.root)
//
//        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAdd) { fab, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            fab.translationY = -systemBars.bottom.toFloat() / 2f // keep above nav bar, add margin if needed
//            insets
//        }


        //Set startup fragment, keep current fragment if dark mode changes
        if (model.getCurrentFragment() != null) {
            replaceFragment(model.getCurrentFragment()!!)
        } else {
            replaceFragment(CommunityFragment())
        }

        //Handle bottom nav view press
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when(menuItem.itemId) {
                R.id.bottom_community -> {
                    replaceFragment(CommunityFragment())
                    model.setCurrentFragment(CommunityFragment())
                    true
                }
                R.id.bottom_map -> {
                    replaceFragment(HotspotFragment())
                    model.setCurrentFragment(HotspotFragment())
                    true
                }
                R.id.bottom_observations -> {
                    replaceFragment(ObservationsFragment())
                    model.setCurrentFragment(ObservationsFragment())
                    true
                }
                R.id.bottom_settings -> {
                    replaceFragment(SettingsFragment())
                    model.setCurrentFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }

        //Floating action button
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, AddSightingMapActivity::class.java)
            startActivity(intent)
        }

    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.frame_container, fragment).commit()
    }

}