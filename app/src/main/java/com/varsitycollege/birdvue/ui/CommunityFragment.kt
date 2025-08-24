package com.varsitycollege.birdvue.ui

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.varsitycollege.birdvue.data.Observation
import com.varsitycollege.birdvue.data.ObservationAdapterCom
import com.varsitycollege.birdvue.databinding.FragmentCommunityBinding
import java.text.ParseException
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger


class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private lateinit var database: FirebaseDatabase
    private lateinit var observationsRef: DatabaseReference
    private lateinit var usersRef: DatabaseReference
    private lateinit var observationComArrayList: ArrayList<Observation>
    private lateinit var observationComRecyclerView: RecyclerView
    //search
    private lateinit var searchView: SearchView
    private lateinit var observationAdapterCom: ObservationAdapterCom

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        observationComRecyclerView = binding.recyclerViewCom
        observationComArrayList = arrayListOf()

        // Initialize Firebase
        database = FirebaseDatabase.getInstance("https://birdvue-9288a-default-rtdb.europe-west1.firebasedatabase.app/")
        observationsRef = database.getReference("observations")


        observationComRecyclerView.layoutManager = LinearLayoutManager(context)
        observationAdapterCom = ObservationAdapterCom(observationComArrayList)
        observationComRecyclerView.adapter = observationAdapterCom

//        val currentUser = FirebaseAuth.getInstance().currentUser
//        if (currentUser != null) {
//            getData(currentUser)
//        }
        // Set up the SearchView
        getData()
        setupSearchView()

        return binding.root
    }
    private fun setupSearchView() {
        searchView = binding.searchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterObservations(newText ?: "")
                return true
            }
        })
    }

    //Search for post by date or by birdname
    private fun filterObservations(query: String) {
        val filteredList = if (query.isEmpty()) {
            observationComArrayList
        } else {
            observationComArrayList.filter { observation ->
                observation.date!!.contains(query, ignoreCase = true) ||
                        observation.birdName!!.contains(query, ignoreCase = true) ||
                        observation.userName!!.contains(query, ignoreCase = true) // filter by username
            }
        }
        updateRecyclerView(filteredList)
    }

    //update list based on filter
    private fun updateRecyclerView(list: List<Observation>) {
        // Instead of creating a new adapter, update the list of the existing one
        // and notify. Or if you prefer new instance, that's fine too.
        // This example assumes you want to create a new one as per your original code.
        observationAdapterCom = ObservationAdapterCom(list)
        observationComRecyclerView.adapter = observationAdapterCom

        if (_binding != null) { // Check binding for null to avoid crashes if view is destroyed
            binding.noItems.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun getData() {
        observationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newObservationsList = mutableListOf<Observation>()
                if (snapshot.exists()) {
                    // The totalObservations and processedCount logic is no longer strictly necessary
                    // because we are not doing asynchronous operations inside the loop anymore.
                    // However, I will leave it for minimal changes as requested,
                    // though it doesn't serve the same purpose of waiting for multiple async calls.

                    val totalObservations = snapshot.childrenCount.toInt() // Can be removed if not using processedCount
                    if (totalObservations == 0) {
                        finalizeDataProcessing(newObservationsList) // Process empty list
                        return
                    }
                    val processedCount = AtomicInteger(0) // Can be removed if not used below

                    for (observationSnapshot in snapshot.children) {
                        val observation = observationSnapshot.getValue(Observation::class.java)
                        if (observation != null) {
                            // userName is now directly available on the observation object
                            // from when it was saved in AddSightingMapActivity.

                            // Fallback for older data that might not have userName populated directly
                            if (observation.userName == null) {
                                if (observation.userId != null) {
                                    // For older posts, you might still log or decide on a default.
                                    // Since we are not fetching anymore, it will remain null or you can set a default.
                                    Log.w("CommunityGetData", "Observation ${observation.id} has null userName, and no fetch is performed. UserID: ${observation.userId}")
                                    observation.userName = "User (Legacy)" // Or "Unknown User", or leave as null if adapter handles it
                                } else {
                                    Log.w("CommunityGetData", "Observation ${observation.id} has null userId and null userName.")
                                    observation.userName = "User ID Missing"
                                }
                            } else {
                                // Log to confirm userName is present for new posts
                                Log.d("CommunityGetData", "Observation ${observation.id} - User: ${observation.userName}, Bird: ${observation.birdName}")
                            }

                            newObservationsList.add(observation)

                            // This processedCount check is now less critical as there's no inner async call.
                            // The loop will complete, and then finalizeDataProcessing will be called once.
                            // If you remove totalObservations and processedCount, this if-block can be removed.
                            if (processedCount.incrementAndGet() == totalObservations) {
                                // This will now always be true only after the loop finishes.
                                // finalizeDataProcessing(newObservationsList) // Moved outside the loop
                            }

                        } else {
                            // Malformed observation data in Firebase
                            Log.w("CommunityGetData", "Skipping malformed observation: ${observationSnapshot.key}")
                            // If you remove totalObservations and processedCount, this if-block can be removed.
                            if (processedCount.incrementAndGet() == totalObservations) {
                                // finalizeDataProcessing(newObservationsList) // Moved outside the loop
                            }
                        }
                    }
                    // Call finalizeDataProcessing once after the loop has processed all snapshots
                    finalizeDataProcessing(newObservationsList)
                } else {
                    // No observations exist at all
                    finalizeDataProcessing(newObservationsList) // newObservationsList will be empty
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CommunityGetData", "Failed to read observations.", error.toException())
                finalizeDataProcessing(emptyList()) // Update UI with empty list on error
            }
        })
    }

    private fun finalizeDataProcessing(list: List<Observation>) {
        observationComArrayList.clear()
        observationComArrayList.addAll(list)
        observationComArrayList.sortByDescending { observation ->
            parseDate(observation.date ?: "")
        }

        // DEBUG LOGGING:
        for (obs in observationComArrayList) {
            Log.d("CommunityDataCheck", "Bird: ${obs.birdName}, UserID: ${obs.userId}, Username: ${obs.userName}")
        }

        // Update the existing adapter's list and notify it
        // This is generally more efficient than creating a new adapter instance every time.
        // However, your updateRecyclerView method creates a new one, so let's stick to that pattern for now
        // if it's intentional.
        updateRecyclerView(observationComArrayList)

        // The visibility of "noItems" is handled within updateRecyclerView
    }

    private fun parseDate(dateString: String): Date {
        val format = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        return try {
            format.parse(dateString) ?: Date(0)
        } catch (e: ParseException) {
            e.printStackTrace()
            Date(0)
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}