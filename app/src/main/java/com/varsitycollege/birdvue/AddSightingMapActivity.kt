package com.varsitycollege.birdvue

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.core.view.View
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.varsitycollege.birdvue.BuildConfig.GOOGLE_MAPS_API_KEY
import com.varsitycollege.birdvue.BuildConfig.BIRD_INFO_AI_API_KEY
import com.varsitycollege.birdvue.api.BirdInfoAPI
import com.varsitycollege.birdvue.api.EBirdAPI
import com.varsitycollege.birdvue.data.BirdCacheDao
import com.varsitycollege.birdvue.data.BirdCacheEntry
import com.varsitycollege.birdvue.data.BirdInfo
import com.varsitycollege.birdvue.data.BirdInfoDatabase
import com.varsitycollege.birdvue.data.Observation
import com.varsitycollege.birdvue.databinding.ActivityAddSightingMapBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class AddSightingMapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener {

    private lateinit var binding: ActivityAddSightingMapBinding
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001 // You can use any integer value here
    private var googleMap: GoogleMap? = null
    private lateinit var userLocation: LatLng
    private lateinit var selectedLocation: LatLng

    private lateinit var overlayLayout: RelativeLayout
    private var uriMap: Uri? = null
    private lateinit var loadingIndicator: ProgressBar
    private var downloadUrl: String? = null
    private var downloadUrlMap: String? = null
    private val _birdInfo = MutableLiveData<BirdInfo>()
    val birdInfo: MutableLiveData<BirdInfo> = _birdInfo
    private var selectedImageUriForAI: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSightingMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Fix layout for insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom) // push content away from bars

            insets
        }

        configureMap()
        // Setup autocomplete
        if (!com.google.android.libraries.places.api.Places.isInitialized()) {
            com.google.android.libraries.places.api.Places.initialize(this, GOOGLE_MAPS_API_KEY)
        }
        setupAutocomplete()

        loadingIndicator = findViewById(R.id.loadingIndicator)
        overlayLayout = findViewById(R.id.overlayLayout)

//        binding.statMap.setOnClickListener{
//            downloadStaticMap()
//        }
        // registers a photo picker activity launcher in single select mode.
        // Link: https://developer.android.com/training/data-storage/shared/photopicker
        // accessed: 13 October 2023
        val pickMedia =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                // this callback is invoked after they choose an image or close the photo picker
                //Set the image of the UI imageview
                binding.openGalleryButton.setImageURI(uri)
                selectedImageUriForAI = uri
                //On submit, upload image then observation
                binding.overviewSubmitButton.setOnClickListener {
                    if (uri != null) {
                        if (binding.birdNameFieldEditText.text.toString().isBlank()) {
                            Log.d("Image URI", "$uri")
                            Toast.makeText(applicationContext, "Please specify a bird name", Toast.LENGTH_LONG).show()
                        } else {
                            showLoadingOverlay()
                            downloadStaticMap(uri)
                        }
                    } else {
                        Toast.makeText(applicationContext, "Please select a photo", Toast.LENGTH_LONG).show()
                        Log.d("PhotoPicker", "No media selected")
                    }
                }

                binding.aiAutofillButton.setOnClickListener {
                    //TODO: use bird name returned from image identification, move to callback for after image picked
                    if (uri != null) {
                        uploadImageForPrediction(uri)
//                        fetchBirdInfoCoroutine("Barn Owl")
                    } else {
                        Toast.makeText(applicationContext, "Please select a photo", Toast.LENGTH_LONG).show()
                        Log.d("PhotoPicker", "No media selected")
                    }
                }
            }

        binding.openGalleryButton.setOnClickListener {
            // Launch the photo picker and let the user choose only images.
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

    }

    private fun prepareFilePart(partName: String, fileUri: Uri): MultipartBody.Part? {
        // ContentResolver to get details and stream data from the URI
        val contentResolver = applicationContext.contentResolver
        try {
            // Get the file name. If it's null, use a generic name.
            var fileName = "uploaded_image.jpg" // Default filename
            contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            // Get an InputStream from the URI
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                // Get the MIME type of the file
                val mimeType = contentResolver.getType(fileUri) ?: "image/*"

                // Create a RequestBody from the InputStream
                // We need the content length for the RequestBody.
                // One way to get it is to copy the stream to a temporary file or byte array.
                // For simplicity here, if your server doesn't strictly require Content-Length for the part,
                // you might get away with creating RequestBody directly from bytes if the image isn't too large.
                // However, the robust way is to stream it.

                val tempFile = File.createTempFile("upload", ".tmp", applicationContext.cacheDir)
                FileOutputStream(tempFile).use { fos ->
                    inputStream.copyTo(fos)
                }
                val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())

                // Create MultipartBody.Part using file name, request body
                return MultipartBody.Part.createFormData(partName, fileName, requestFile)
            }
        } catch (e: Exception) {
            Log.e("PrepareFilePart", "Error preparing file part: ${e.message}", e)
            Toast.makeText(applicationContext, "Error preparing image for upload", Toast.LENGTH_SHORT).show()
            return null
        }
        return null
    }

    private fun uploadImageForPrediction(imageUri: Uri) {
        showLoadingOverlay() // Show loading indicator

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val imageFilePart = withContext(Dispatchers.IO) { // Perform file operations on IO thread
                    prepareFilePart("file", imageUri)
                }

                if (imageFilePart == null) {
                    Toast.makeText(applicationContext, "Could not prepare image for upload.", Toast.LENGTH_LONG).show()
                    hideLoadingOverlay()
                    return@launch
                }

                val predictUrl = "https://sveiaufbgb.eu-west-1.awsapprunner.com/predict"
                Log.d("UploadImage", "Uploading to: $predictUrl with file part: ${imageFilePart.headers}")

                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS) // time allowed to establish connection
                    .readTimeout(30, TimeUnit.SECONDS)    // time allowed for server to send response
                    .writeTimeout(30, TimeUnit.SECONDS)   // time allowed to send request body
                    .build()
                //Call eBird api to fetch hotspot data
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val api = retrofit.create(BirdInfoAPI::class.java)

                val response = api.predictImage(predictUrl, imageFilePart)

                if (response.isSuccessful) {
                    val prediction = response.body()
                    if (prediction?.predictedClass != null) {
                        Toast.makeText(applicationContext, "Predicted: ${prediction.predictedClass}", Toast.LENGTH_LONG).show()
                        Log.i("UploadImage", "Prediction successful: ${response.body()}")
                        //hideLoadingOverlay()
                        // Now use this predicted_bird_name to call your fetchBirdInfoCoroutine
                        fetchBirdInfoCoroutine(prediction.predictedClass)
                        // The fetchBirdInfoCoroutine will handle its own loading overlay,
                        // so we can hide the current one if it's separate.
                        // If fetchBirdInfoCoroutine shows its own overlay, ensure they don't overlap awkwardly.
                    } else {
                        Toast.makeText(applicationContext, "AI could not identify the bird.", Toast.LENGTH_LONG).show()
                        Log.w("UploadImage", "Prediction successful but no bird name in response: ${response.body()}")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(applicationContext, "AI prediction failed: ${response.code()}", Toast.LENGTH_LONG).show()
                    Log.e("UploadImage", "API Error: ${response.code()} - ${response.message()}. Body: $errorBody")
                }
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Error during AI prediction: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("UploadImage", "Exception: ${e.message}", e)
            } finally {
                // If fetchBirdInfoCoroutine has its own overlay logic, this might be handled there.
                // Otherwise, ensure the overlay is hidden appropriately.
                // For now, let's assume fetchBirdInfoCoroutine will manage the overlay state from this point.
                // If not, uncomment hideLoadingOverlay() and re-enable the button.
                hideLoadingOverlay()
            }
        }
    }

    private val birdDao: BirdCacheDao by lazy {
        BirdInfoDatabase.getDatabase(applicationContext).birdCacheDao()
    }

    private fun fetchBirdInfoCoroutine(birdName: String) {
        Toast.makeText(applicationContext, "Fetching bird info", Toast.LENGTH_SHORT).show()
        //loading indicator here
         showLoadingOverlay()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. Check cache first
                val cachedBird = withContext(Dispatchers.IO) { // Perform DB operations on IO dispatcher
                    birdDao.getBirdByName(birdName)
                }

                val threeDaysInMillis = 3 * 24 * 60 * 60 * 1000L
                val isCacheStale = cachedBird?.let { (System.currentTimeMillis() - it.timestamp) > threeDaysInMillis } ?: true

                if (cachedBird != null && !isCacheStale) {
                    Toast.makeText(applicationContext, "Fetched from cache: $birdName", Toast.LENGTH_SHORT).show()
                    // Update UI with cached data
                    _birdInfo.postValue(BirdInfo(
                        prompt = cachedBird.birdName,
                        answer = cachedBird.description
                    ))
                    binding.birdNameFieldEditText.setText(cachedBird.birdName)
                    binding.detailsFieldEditText.setText(cachedBird.description)
                    Log.d("BirdInfoCache", "Bird info fetched from cache: ${cachedBird.birdName}")
                } else {
                    Toast.makeText(applicationContext, "Fetching from API: $birdName", Toast.LENGTH_SHORT).show()
                    //Call eBird api to fetch hotspot data
                    val retrofit = Retrofit.Builder()
                        .baseUrl("https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()

                    val api = retrofit.create(BirdInfoAPI::class.java)

                    val response = api.getBirdInfo(birdName, BIRD_INFO_AI_API_KEY)
                    if (response.isSuccessful) {
                        val birdInfo = response.body()
                        if (birdInfo != null && birdInfo.prompt != null && birdInfo.answer != null) {
                            _birdInfo.postValue(birdInfo)
                            binding.birdNameFieldEditText.setText(birdInfo.prompt)
                            binding.detailsFieldEditText.setText(birdInfo.answer)
                            Log.d("BirdInfoAPI", "Bird info fetched successfully: ${response.body()?: "null"}")

                            // 3. Save to cache
                            val newCacheEntry = BirdCacheEntry(
                                birdName = birdInfo.prompt,
                                description = birdInfo.answer,
                                timestamp = System.currentTimeMillis()
                            )
                            withContext(Dispatchers.IO) {
                                BirdInfoDatabase.insertAndManageCache(applicationContext, newCacheEntry)
                            }
                        } else {
                            Log.e("BirdInfoAPI", "API response body or details are null.")
                            Toast.makeText(applicationContext, "Could not fetch details for $birdName", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e("BirdInfoAPI", "API Error: ${response.code()}")
                        Toast.makeText(applicationContext, "Could not fetch details for $birdName", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("BirdInfoCoroutine", "Error fetching bird info for $birdName: ${e.message}", e)
                Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                hideLoadingOverlay()
            }

        }
    }

    private fun setupAutocomplete() {
        val autocompleteFragment = supportFragmentManager
            .findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        // Specify which fields you want returned
        autocompleteFragment.setPlaceFields(listOf(
            com.google.android.libraries.places.api.model.Place.Field.ID,
            com.google.android.libraries.places.api.model.Place.Field.NAME,
            com.google.android.libraries.places.api.model.Place.Field.LAT_LNG
        ))

        autocompleteFragment.setOnPlaceSelectedListener(object :
            com.google.android.libraries.places.widget.listener.PlaceSelectionListener {
            override fun onPlaceSelected(place: com.google.android.libraries.places.api.model.Place) {
                // Move camera to selected location
                place.latLng?.let { latLng ->
                    selectedLocation = latLng  // Update the selected location
                    googleMap?.clear()  // Remove previous markers
                    googleMap?.addMarker(MarkerOptions().position(latLng).title(place.name))
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                //Toast.makeText(this@AddSightingMapActivity, "Error: $status", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun configureMap() {
        val supportMapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        supportMapFragment.getMapAsync(this)
        // Async map
        supportMapFragment.getMapAsync(OnMapReadyCallback { googleMap ->
            // Allows user to select a custom location instead of their current location
            googleMap.setOnMapClickListener { latLng ->
                // When clicked on map
                // Update selected location
                selectedLocation = latLng
                // Initialize marker options
                val markerOptions = MarkerOptions()
                // Set position of marker
                markerOptions.position(latLng)
                // Set title of marker
                markerOptions.title("${latLng.latitude} : ${latLng.longitude}")
                // Remove all markers
                googleMap.clear()
                // Animating to zoom the marker
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                // Add marker on map
                googleMap.addMarker(markerOptions)
            }
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
        })
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        userLocation = LatLng(it.latitude, it.longitude)
                        selectedLocation = userLocation
                        googleMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                userLocation,
                                15f
                            )
                        )
                    }
                }
        } else {
            // Request location permissions
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, enable my location
                enableMyLocation()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        enableMyLocation()
    }

    override fun onMyLocationButtonClick(): Boolean {
        // Reset the selected location to user's location
        selectedLocation = userLocation
        // Remove all markers
        googleMap?.clear()
        return false // Return false to let the default behavior occur
    }

    private fun uploadImage(imageUri: Uri?) {
        // Generate a file name based on current time in milliseconds
        val fileName = "photo_${System.currentTimeMillis()}"
        // Get a reference to the Firebase Storage
        val storageRef = FirebaseStorage.getInstance().reference.child("images/")
        // Create a reference to the file location in Firebase Storage
        val imageRef = storageRef.child(fileName)

        val uploadTask = imageRef.putFile(imageUri!!)
        uploadTask.addOnCompleteListener {
            if (it.isSuccessful) {
                // Image upload successful
                imageRef.downloadUrl.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uri = task.result
                        downloadUrl = uri.toString()
                        //Upload observation after getting the download URL
                        submitObservation()
                        hideLoadingOverlay()
                    } else {
                        // Image upload failed
                        Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        uploadTask.addOnFailureListener {
            Toast.makeText(applicationContext, it.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun submitObservation() {
        try {
            val dp = binding.datePicker1
            val database =
                FirebaseDatabase.getInstance("https://birdvue-9288a-default-rtdb.europe-west1.firebasedatabase.app/")
            val ref = database.getReference("observations")
            val key = ref.push().key
            val observation = Observation(
                id = key!!, // use a unique ID or use push() in Firebase
                birdName = binding.birdNameFieldEditText.text.toString(),
                date = "${dp.year}/${dp.month + 1}/${dp.dayOfMonth}",
                photo = downloadUrl,
                details = binding.detailsFieldEditText.text.toString(),
                lat = selectedLocation.latitude,
                lng = selectedLocation.longitude,
                location = downloadUrlMap,
                likes = 0, // ahd to put something here because of the data class
                comments = emptyList(), // theres no comments for now i know
                userId = FirebaseAuth.getInstance().currentUser!!.uid
            )

            // pushing the data for observation to firebase
            // link: https://www.geeksforgeeks.org/how-to-save-data-to-the-firebase-realtime-database-in-android/
            // accessed: 13 October 2023
            ref.child(key).setValue(observation).addOnSuccessListener {
                Toast.makeText(
                    applicationContext,
                    "Observation was added successfully.",
                    Toast.LENGTH_LONG
                ).show()
                //Go back to the home page
                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)
                hideLoadingOverlay()
            }.addOnFailureListener { e ->
                Toast.makeText(
                    applicationContext,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                hideLoadingOverlay()
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_LONG).show()
            hideLoadingOverlay()
        }
    }

    private suspend fun uploadStaticMapImage(
        apiKey: String,
        center: String,
        zoom: Int,
        size: String,
        scale: Int,
        format: String
    ): String? {
        return withContext(Dispatchers.IO) {
            val marker = "markers=size:mid%7Ccolor:red%7Clabel:A%7C$center"
            val url = "https://maps.googleapis.com/maps/api/staticmap?" +
                    "center=$center&" +
                    "zoom=$zoom&" +
                    "size=$size&" +
                    "scale=$scale&" +
                    "format=$format&" +
                    "$marker&" +
                    "key=$apiKey"
            Log.d("StaticMapImage", "URL: $url") // Print the URL to logcat
            val imageUrl = URL(url)
            val imageStream: InputStream = imageUrl.openStream()
            val fileName = "photo_${System.currentTimeMillis()}"
            val storageRef = FirebaseStorage.getInstance().reference.child("images/map_images")
            val mapImageRef = storageRef.child(fileName)
            val uploadTask = mapImageRef.putStream(imageStream)
            uploadTask.await() // Wait for the upload task to complete
            mapImageRef.downloadUrl.await().toString() // Get and return the download URL
        }
    }
    private fun downloadStaticMap(imageUri: Uri?) {
        val apiKey = GOOGLE_MAPS_API_KEY
        val center =
            "${selectedLocation.latitude},${selectedLocation.longitude}" // Latitude,Longitude
        val zoom = 18
        val size = "640x480"
        val scale = 2
        val format = "png"
        val filePath = "photoMap_${System.currentTimeMillis()}"
//        test

        CoroutineScope(Dispatchers.Main).launch {
             downloadUrlMap = withContext(Dispatchers.IO) {
            uploadStaticMapImage(apiKey, center, zoom, size, scale, format)
            }
            uploadImage(imageUri)
            Log.d("Image URI", "$imageUri")
        }
    }
    private fun showLoadingOverlay() {
        overlayLayout.visibility = android.view.View.VISIBLE
        loadingIndicator.visibility = android.view.View.VISIBLE
        binding.aiAutofillButton.isEnabled = false
        binding.overviewSubmitButton.isEnabled = false
    }

    private fun hideLoadingOverlay() {
        overlayLayout.visibility = android.view.View.GONE
        loadingIndicator.visibility = android.view.View.INVISIBLE
        binding.aiAutofillButton.isEnabled = true
        binding.overviewSubmitButton.isEnabled = true
    }

}
