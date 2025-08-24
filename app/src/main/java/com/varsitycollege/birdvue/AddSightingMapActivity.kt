package com.varsitycollege.birdvue

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.varsitycollege.birdvue.BuildConfig.BIRD_INFO_AI_API_KEY
import com.varsitycollege.birdvue.BuildConfig.GOOGLE_MAPS_API_KEY
import com.varsitycollege.birdvue.api.BirdInfoAPI
import com.varsitycollege.birdvue.data.BirdCacheDao
import com.varsitycollege.birdvue.data.BirdCacheEntry
import com.varsitycollege.birdvue.data.BirdInfo
import com.varsitycollege.birdvue.data.BirdInfoDatabase
import com.varsitycollege.birdvue.data.Observation
import com.varsitycollege.birdvue.databinding.ActivityAddSightingMapBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

class AddSightingMapActivity : AppCompatActivity(), OnMapReadyCallback,
    GoogleMap.OnMyLocationButtonClickListener {

    private lateinit var binding: ActivityAddSightingMapBinding
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var googleMap: GoogleMap? = null
    private lateinit var userLocation: LatLng
    private lateinit var selectedLocation: LatLng

    private lateinit var overlayLayout: RelativeLayout
    private lateinit var loadingIndicator: ProgressBar

    private var uriMap: Uri? = null
    private var downloadUrl: String? = null
    private var downloadUrlMap: String? = null

    private val _birdInfo = MutableLiveData<BirdInfo>()
    val birdInfo: MutableLiveData<BirdInfo> = _birdInfo

    private var selectedImageUriForAI: Uri? = null

    private val VERIFY_IMAGE_URL =
        "https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/verifybirdimage"
    private val PREDICT_SPECIES_URL = "https://sveiaufbgb.eu-west-1.awsapprunner.com/predict"

    private lateinit var usersRef: DatabaseReference

    private val birdDao: BirdCacheDao by lazy {
        BirdInfoDatabase.getDatabase(applicationContext).birdCacheDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSightingMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        usersRef = FirebaseDatabase
            .getInstance("https://birdvue-9288a-default-rtdb.europe-west1.firebasedatabase.app/")
            .getReference("users")

        loadingIndicator = findViewById(R.id.loadingIndicator)
        overlayLayout = findViewById(R.id.overlayLayout)

        configureMap()
        ensurePlacesInitialized()
        setupAutocomplete()

        val pickMedia =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                // user picked or dismissed
                binding.openGalleryButton.setImageURI(uri)
                selectedImageUriForAI = uri

                binding.overviewSubmitButton.setOnClickListener {
                    if (uri == null) {
                        toast("Please select a photo")
                        return@setOnClickListener
                    }
                    if (binding.birdNameFieldEditText.text.toString().isBlank()) {
                        toast("Please specify a bird name")
                        return@setOnClickListener
                    }
                    showLoadingOverlay()
                    CoroutineScope(Dispatchers.Main).launch {
                        val isBird = checkIfBird(uri)
                        if (isBird) {
                            downloadStaticMap(uri) // continues upload flow
                        } else {
                            toast("This image is not a bird")
                            hideLoadingOverlay()
                        }
                    }
                }

                binding.aiAutofillButton.setOnClickListener {
                    if (uri == null) {
                        toast("Please select a photo")
                        return@setOnClickListener
                    }
                    showLoadingOverlay()
                    CoroutineScope(Dispatchers.Main).launch {
                        val isBird = checkIfBird(uri)
                        if (isBird) {
                            toast("Successfully identified as a bird, predicting type...")
                            uploadImageForPrediction(uri) // predict + fetchBirdInfo
                        } else {
                            toast("This image is not a bird")
                            hideLoadingOverlay()
                        }
                    }
                }
            }

        binding.openGalleryButton.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    // --- AI verification / prediction ---

    private suspend fun checkIfBird(imageUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val imageFilePart = prepareFilePart("file", imageUri) ?: return@withContext false
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val api = retrofit.create(BirdInfoAPI::class.java)
            val resp = api.verifyBirdImage(VERIFY_IMAGE_URL, imageFilePart, BIRD_INFO_AI_API_KEY)

            if (resp.isSuccessful && resp.body()?.isOk == true) {
                Log.d("AIProcess", "Verified as bird: ${resp.body()}")
                true
            } else {
                Log.w("AIProcess", "Not a bird or API error: ${resp.code()}, Body: ${resp.body()}")
                false
            }
        } catch (e: Exception) {
            Log.e("AIProcess", "Error verifying bird: ${e.message}", e)
            false
        }
    }

    private fun prepareFilePart(partName: String, fileUri: Uri): MultipartBody.Part? {
        val cr = applicationContext.contentResolver
        return try {
            var fileName = "uploaded_image.jpg"
            cr.query(fileUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) fileName = cursor.getString(idx)
                }
            }
            cr.openInputStream(fileUri)?.use { inputStream ->
                val mime = cr.getType(fileUri) ?: "image/*"
                val temp = File.createTempFile("upload", ".tmp", applicationContext.cacheDir)
                FileOutputStream(temp).use { fos -> inputStream.copyTo(fos) }
                val req = temp.asRequestBody(mime.toMediaTypeOrNull())
                MultipartBody.Part.createFormData(partName, fileName, req)
            }
        } catch (e: Exception) {
            Log.e("PrepareFilePart", "Error preparing file part: ${e.message}", e)
            toast("Error preparing image for upload")
            null
        }
    }

    private fun uploadImageForPrediction(imageUri: Uri) {
        showLoadingOverlay()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val part = withContext(Dispatchers.IO) { prepareFilePart("file", imageUri) }
                if (part == null) {
                    toast("Could not prepare image for upload.")
                    hideLoadingOverlay(); return@launch
                }

                Log.d("UploadImage", "Uploading to: $PREDICT_SPECIES_URL")

                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl("https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val api = retrofit.create(BirdInfoAPI::class.java)
                val resp = api.predictImage(PREDICT_SPECIES_URL, part, BIRD_INFO_AI_API_KEY)

                if (resp.isSuccessful) {
                    val prediction = resp.body()
                    if (prediction?.predictedClass != null) {
                        toast("Predicted: ${prediction.predictedClass}")
                        fetchBirdInfoCoroutine(prediction.predictedClass)
                    } else {
                        toast("AI could not identify the bird.")
                        Log.w("UploadImage", "Prediction ok but no class: ${resp.body()}")
                    }
                } else {
                    toast("AI prediction failed: ${resp.code()}")
                    Log.e("UploadImage", "API Error: ${resp.code()} - ${resp.message()}")
                }
            } catch (e: Exception) {
                toast("Error during AI prediction: ${e.message}")
                Log.e("UploadImage", "Exception", e)
            } finally {
                // overlay gets hidden by fetchBirdInfoCoroutine or later
            }
        }
    }

    private fun fetchBirdInfoCoroutine(birdName: String) {
        toast("Fetching bird info")
        showLoadingOverlay()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val cached = withContext(Dispatchers.IO) { birdDao.getBirdByName(birdName) }
                val staleMs = 3 * 24 * 60 * 60 * 1000L
                val isStale = cached?.let { (System.currentTimeMillis() - it.timestamp) > staleMs } ?: true

                if (cached != null && !isStale) {
                    _birdInfo.postValue(BirdInfo(prompt = cached.birdName, answer = cached.description))
                    binding.birdNameFieldEditText.setText(cached.birdName)
                    binding.detailsFieldEditText.setText(cached.description)
                    Log.d("BirdInfoCache", "From cache: ${cached.birdName}")
                } else {
                    val retrofit = Retrofit.Builder()
                        .baseUrl("https://kpcs4l6aa3.execute-api.eu-west-1.amazonaws.com/BirdRESTApiStage/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()

                    val api = retrofit.create(BirdInfoAPI::class.java)
                    val resp = api.getBirdInfo(birdName, BIRD_INFO_AI_API_KEY)

                    if (resp.isSuccessful) {
                        val info = resp.body()
                        if (info != null && info.prompt != null && info.answer != null) {
                            _birdInfo.postValue(info)
                            binding.birdNameFieldEditText.setText(info.prompt)
                            binding.detailsFieldEditText.setText(info.answer)

                            val entry = BirdCacheEntry(
                                birdName = info.prompt,
                                description = info.answer,
                                timestamp = System.currentTimeMillis()
                            )
                            withContext(Dispatchers.IO) {
                                BirdInfoDatabase.insertAndManageCache(applicationContext, entry)
                            }
                        } else {
                            toast("Could not fetch details for $birdName")
                        }
                    } else {
                        toast("Could not fetch details for $birdName")
                        Log.e("BirdInfoAPI", "API Error: ${resp.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("BirdInfoCoroutine", "Error fetching info for $birdName", e)
                toast("Error: ${e.message}")
            } finally {
                hideLoadingOverlay()
            }
        }
    }

    // --- Places / Map ---

    private fun ensurePlacesInitialized() {
        if (!com.google.android.libraries.places.api.Places.isInitialized()) {
            com.google.android.libraries.places.api.Places.initialize(this, GOOGLE_MAPS_API_KEY)
        }
    }

    private fun setupAutocomplete() {
        val frag = supportFragmentManager
            .findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        frag.setPlaceFields(
            listOf(
                com.google.android.libraries.places.api.model.Place.Field.ID,
                com.google.android.libraries.places.api.model.Place.Field.NAME,
                com.google.android.libraries.places.api.model.Place.Field.LAT_LNG
            )
        )

        frag.setOnPlaceSelectedListener(object :
            com.google.android.libraries.places.widget.listener.PlaceSelectionListener {
            override fun onPlaceSelected(place: com.google.android.libraries.places.api.model.Place) {
                place.latLng?.let { latLng ->
                    selectedLocation = latLng
                    googleMap?.clear()
                    googleMap?.addMarker(MarkerOptions().position(latLng).title(place.name))
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                // no-op
            }
        })
    }

    private fun configureMap() {
        val frag = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        frag.getMapAsync(this)
        frag.getMapAsync { map ->
            map.setOnMapClickListener { latLng ->
                selectedLocation = latLng
                val markerOptions = MarkerOptions().position(latLng).title("${latLng.latitude} : ${latLng.longitude}")
                map.clear()
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                map.addMarker(markerOptions)
            }
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        }
    }

    private fun enableMyLocation() {
        val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION
        if (ActivityCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, coarse) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { loc ->
                    loc?.let {
                        userLocation = LatLng(it.latitude, it.longitude)
                        selectedLocation = userLocation
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))
                    }
                }
        } else {
            requestPermissions(arrayOf(coarse, fine), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        enableMyLocation()
    }

    override fun onMyLocationButtonClick(): Boolean {
        selectedLocation = userLocation
        googleMap?.clear()
        return false
    }

    // --- Upload flows ---

    private fun uploadImage(imageUri: Uri?) {
        val fileName = "photo_${System.currentTimeMillis()}"
        val storageRef = FirebaseStorage.getInstance().reference.child("images/")
        val imageRef = storageRef.child(fileName)

        val task = imageRef.putFile(imageUri ?: return)
        task.addOnCompleteListener {
            if (it.isSuccessful) {
                imageRef.downloadUrl.addOnCompleteListener { t ->
                    if (t.isSuccessful) {
                        downloadUrl = t.result.toString()
                        submitObservation()
                        hideLoadingOverlay()
                    } else {
                        toast("Failed to upload image")
                    }
                }
            }
        }.addOnFailureListener { ex ->
            toast(ex.localizedMessage ?: "Upload failed")
        }
    }

    private fun submitObservation() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            toast("You must be logged in to submit.")
            return
        }
        if (!::selectedLocation.isInitialized) {
            toast("Location not selected. Please pick a location on the map.")
            return
        }
        val userId = currentUser.uid

        usersRef.child(userId)
            .child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.getValue(String::class.java) ?: "Anonymous"
                    try {
                        val dp = binding.datePicker1
                        val db = FirebaseDatabase.getInstance(
                            "https://birdvue-9288a-default-rtdb.europe-west1.firebasedatabase.app/"
                        )
                        val ref = db.getReference("observations")
                        val key = ref.push().key ?: return

                        val observation = Observation(
                            id = key,
                            birdName = binding.birdNameFieldEditText.text.toString(),
                            date = "${dp.year}/${dp.month + 1}/${dp.dayOfMonth}",
                            photo = downloadUrl,
                            details = binding.detailsFieldEditText.text.toString(),
                            lat = selectedLocation.latitude,
                            lng = selectedLocation.longitude,
                            location = downloadUrlMap,
                            likes = 0,
                            comments = emptyList(),
                            userId = userId,
                            userName = username
                        )

                        ref.child(key).setValue(observation)
                            .addOnSuccessListener {
                                toast("Observation was added successfully.")
                                startActivity(Intent(this@AddSightingMapActivity, HomeActivity::class.java))
                                hideLoadingOverlay()
                            }
                            .addOnFailureListener { e ->
                                toast("Error: ${e.message}")
                                hideLoadingOverlay()
                            }
                    } catch (e: Exception) {
                        toast(e.localizedMessage ?: "Unknown error")
                        hideLoadingOverlay()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    toast("Database error: ${error.message}")
                }
            })
    }

    // --- Static maps (separate helpers) ---

    private suspend fun uploadStaticMapImage(
        apiKey: String,
        center: String,
        zoom: Int,
        size: String,
        scale: Int,
        format: String
    ): String? = withContext(Dispatchers.IO) {
        val marker = "markers=size:mid%7Ccolor:red%7Clabel:A%7C$center"
        val url = "https://maps.googleapis.com/maps/api/staticmap?" +
                "center=$center&zoom=$zoom&size=$size&scale=$scale&format=$format&$marker&key=$apiKey"
        Log.d("StaticMapImage", "URL: $url")
        val imageUrl = URL(url)
        val imageStream: InputStream = imageUrl.openStream()
        val fileName = "photo_${System.currentTimeMillis()}"
        val storageRef = FirebaseStorage.getInstance().reference.child("images/map_images")
        val mapImageRef = storageRef.child(fileName)
        val uploadTask = mapImageRef.putStream(imageStream)
        uploadTask.await()
        mapImageRef.downloadUrl.await().toString()
    }

    private fun downloadStaticMap(imageUri: Uri?) {
        val apiKey = GOOGLE_MAPS_API_KEY
        val center = "${selectedLocation.latitude},${selectedLocation.longitude}"
        val zoom = 18
        val size = "640x480"
        val scale = 2
        val format = "png"

        CoroutineScope(Dispatchers.Main).launch {
            downloadUrlMap = uploadStaticMapImage(apiKey, center, zoom, size, scale, format)
            uploadImage(imageUri)
            Log.d("Image URI", "$imageUri")
        }
    }

    // --- UI helpers ---

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

    private fun toast(msg: String) =
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
}
