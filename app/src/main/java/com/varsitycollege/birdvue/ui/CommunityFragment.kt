package com.varsitycollege.birdvue.ui

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import java.util.Locale
import java.util.Date
import java.text.ParseException
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
        usersRef = database.getReference("users")


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
                    val totalObservations = snapshot.childrenCount.toInt()
                    if (totalObservations == 0) {
                        finalizeDataProcessing(newObservationsList) // Process empty list
                        return
                    }

                    val processedCount = AtomicInteger(0)

                    for (observationSnapshot in snapshot.children) {
                        val observation = observationSnapshot.getValue(Observation::class.java)
                        if (observation != null) {
                            observation.userId?.let { userId ->
                                usersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(userSnapshot: DataSnapshot) {
                                        val username = userSnapshot.child("username").getValue(String::class.java)
                                        observation.userName = username ?: "Unknown User"
                                        newObservationsList.add(observation)

                                        if (processedCount.incrementAndGet() == totalObservations) {
                                            finalizeDataProcessing(newObservationsList)
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        Log.e("CommunityGetData", "Error fetching username for ${observation.userId}", error.toException())
                                        observation.userName = "Unknown User"
                                        newObservationsList.add(observation)
                                        if (processedCount.incrementAndGet() == totalObservations) {
                                            finalizeDataProcessing(newObservationsList)
                                        }
                                    }
                                })
                            } ?: run {
                                // userId is null on the observation
                                Log.w("CommunityGetData", "Observation ${observation.id} has null userId")
                                observation.userName = "User ID Missing"
                                newObservationsList.add(observation)
                                if (processedCount.incrementAndGet() == totalObservations) {
                                    finalizeDataProcessing(newObservationsList)
                                }
                            }
                        } else {
                            // Malformed observation data in Firebase
                            Log.w("CommunityGetData", "Skipping malformed observation: ${observationSnapshot.key}")
                            if (processedCount.incrementAndGet() == totalObservations) {
                                // Still need to count it to avoid getting stuck
                                finalizeDataProcessing(newObservationsList)
                            }
                        }
                    }
                } else {
                    finalizeDataProcessing(newObservationsList)
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