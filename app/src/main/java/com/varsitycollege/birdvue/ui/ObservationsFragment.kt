package com.varsitycollege.birdvue.ui

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.varsitycollege.birdvue.data.Observation
import com.varsitycollege.birdvue.data.ObservationAdapter
import com.varsitycollege.birdvue.databinding.FragmentObservationsBinding
import java.text.ParseException
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ObservationsFragment : Fragment() {

    private var _binding: FragmentObservationsBinding? = null
    private lateinit var database: FirebaseDatabase
    private lateinit var observationsRef: DatabaseReference
    private lateinit var usersRef: DatabaseReference
    private lateinit var observationArrayList: ArrayList<Observation>
    private lateinit var observationRecyclerView: RecyclerView
    //search
    private lateinit var searchView: SearchView
    private lateinit var observationsAdapter: ObservationAdapter
    private var currentUser: FirebaseUser? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentObservationsBinding.inflate(inflater, container, false)
        observationRecyclerView = binding.recyclerViewCom
        observationArrayList = arrayListOf()

        // Initiate Firebase a bit earlier
        database = FirebaseDatabase.getInstance("https://birdvue-9288a-default-rtdb.europe-west1.firebasedatabase.app/")
        observationsRef = database.getReference("observations")
        usersRef = database.getReference("users")

        observationRecyclerView.layoutManager = LinearLayoutManager(context)
        // Initialize the adapter with an empty List initially
        observationsAdapter = ObservationAdapter(observationArrayList)
        observationRecyclerView.adapter = observationsAdapter

        currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            getData(it)
        }

        // Set up the SearchView
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
            observationArrayList
        } else {
            observationArrayList.filter { observation ->
                observation.date!!.contains(query, ignoreCase = true) ||
                observation.birdName!!.contains(query, ignoreCase = true)
            }
        }
        updateRecyclerView(filteredList)
    }

    //update list based on filter
    private fun updateRecyclerView(list: List<Observation>) {
        observationsAdapter = ObservationAdapter(list)
        observationRecyclerView.adapter = observationsAdapter
        if (_binding != null) {
            binding.noItems.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun getData(user: FirebaseUser) {
        observationsRef.addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newObservationList = mutableListOf<Observation>()
                if (snapshot.exists()) {
                    val userObservationsSnapshots = snapshot.children.filter {
                        it.getValue(Observation::class.java)?.userId == user.uid
                    }
                    val totalUserObservations = userObservationsSnapshots.size

                    if (totalUserObservations == 0) {
                        finalizeDataProcessing(newObservationList)
                        return
                    }

                    val processedCount = AtomicInteger(0)

                    // First, get the current user's username once
                    usersRef.child(user.uid).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnapshot: DataSnapshot) {
                                val currentUsername =
                                    userSnapshot.child("username").getValue(String::class.java)
                                        ?: "Me"

                                for (observationSnapshot in userObservationsSnapshots) {
                                    val observation = observationSnapshot.getValue(Observation::class.java)
                                    if (observation != null) { // This check is somewhat redundant due to filter above, but safe
                                        observation.userName = currentUsername // Set the fetched username
                                        newObservationList.add(observation)
                                    }
                                    // No nested async call per observation, so processing is simpler
                                }
                                // All user's observations processed with their username
                                finalizeDataProcessing(newObservationList)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                // Failed to get username, proceed without it or with a default for all
                                for (observationSnapshot in userObservationsSnapshots) {
                                    val observation =
                                        observationSnapshot.getValue(Observation::class.java)
                                    if (observation != null) {
                                        observation.userName = "Error loading name"
                                        newObservationList.add(observation)
                                    }
                                }
                                finalizeDataProcessing(newObservationList)
                            }
                        })
                } else {
                    finalizeDataProcessing(newObservationList)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error fetching observations
                // Toast.makeText(context, "There was an error during data retrieval: ${error.message}", Toast.LENGTH_SHORT).show()
                if (_binding != null) binding.noItems.visibility = View.VISIBLE
            }
        })
    }

    private fun finalizeDataProcessing(list: List<Observation>) {
        observationArrayList.clear()
        observationArrayList.addAll(list)
        observationArrayList.sortByDescending { observation ->
            parseDate(observation.date ?: "")
        }
        // Update the adapter with the new complete list
        // No need to create a new adapter instance here if observationAdapter is already initialized
        // and its reference is passed to updateRecyclerView or if you update its internal list and call notifyDataSetChanged.
        // However, updateRecyclerView creates a new instance, so call that.
        updateRecyclerView(observationArrayList) // This will create a new adapter instance
    }

    private fun parseDate(dateString: String): Date {
        val format = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        return try {
            format.parse(dateString) ?: Date(0)
        } catch (e: ParseException) {
            e.printStackTrace()
            Date(0) // Return epoch on error
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}