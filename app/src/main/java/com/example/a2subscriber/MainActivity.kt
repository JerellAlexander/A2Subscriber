package com.example.a2subscriber

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.net.ParseException
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Optional
import java.util.UUID
import kotlin.math.abs

class MapActivity : AppCompatActivity(), OnMapReadyCallback, OnMoreButtonClickListener {

    private var isMapReady = false
    private lateinit var googleMap: GoogleMap
    private lateinit var databaseHelper: DatabaseHelper
    private val markersMap = hashMapOf<Int, MutableList<CustomMarkerPoints>>()
    private val recentMarkersMap = hashMapOf<Int, MutableList<CustomMarkerPoints>>()
    private val studentData = mutableListOf<StudentInfo>()
    private var mqttClient: Mqtt5BlockingClient? = null
    var studentAdapter: StudentAdapter? = null
    var currentStudentId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()

        // Adjust padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = DatabaseHelper(this, null)

        // Set up the map fragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        mqttClient = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816023353.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        setupDateRangeListeners()
        setupStudentAdapter()
        connectAndSubscribe()

        populateStudentData()
        databaseHelper.logAllData()
    }

    private fun connectAndSubscribe() {
        try {
            mqttClient?.connect()
            mqttClient?.toAsync()?.subscribeWith()
                ?.topicFilter("assignment/location")
                ?.callback { message ->
                    val receivedPayload = message.payload
                        .flatMap { Optional.ofNullable(it) }
                        .map { byteBuffer ->
                            val byteArray = ByteArray(byteBuffer.remaining())
                            byteBuffer.get(byteArray)
                            String(byteArray)
                        }.orElse(null)
                    processPayload(receivedPayload)
                    runOnUiThread {
                        //textView.text = "Received: $receivedPayload"
                    }
                }
                ?.send()
        } catch (e: Exception) {
            Toast.makeText(this, "Error connecting or subscribing", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient?.disconnect()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        isMapReady = true
        // Now that the map is ready, we can draw the initial data
        drawRecentMarkers()
    }

    private fun addMarkersForStudent(id: Int) {
        val locationList = databaseHelper.getLocationDataById(id)
        Log.d("MapDrawing", "Retrieved ${locationList.size} points for ID: $id")
        markersMap[id] = locationList
    }

    private fun addRecentMarkersForStudent(id: Int) {
        val locationList = databaseHelper.getRecentLocationData(id)
        Log.d("MapDrawing", "Retrieved ${locationList.size} points for ID: $id")
        recentMarkersMap[id] = locationList
    }

    private fun drawPolylineForStudent(id: Int) {
        // Extract LatLng points from the custom marker points
        val points = markersMap[id]
        val color = getColorForId(id)
        val latLngPoints = points?.map { it.point }

        // Draw a polyline connecting all markers
        val polylineOptions = latLngPoints?.let {
            PolylineOptions()
                .addAll(it)
                .color(color)
                .width(5f)
                .geodesic(true)
        }

        latLngPoints?.firstOrNull()?.let { firstPoint ->
            googleMap.addMarker(
                MarkerOptions()
                    .position(firstPoint)
                    .title("Last Position")
                    .snippet("ID: $id")
                    .icon(BitmapDescriptorFactory.defaultMarker(getHueFromColor(color)))
            )
        }

        if (polylineOptions != null) {
            googleMap.addPolyline(polylineOptions)
        }

        // Adjust the camera view to fit all points
        val boundsBuilder = LatLngBounds.Builder()
        latLngPoints?.forEach { boundsBuilder.include(it) }
        val bounds = boundsBuilder.build()

        // Move camera to show all points with padding
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    private fun drawPolylineForRecent(id: Int) {
        if (!isMapReady) {
            Log.d("MapDrawing", "Map not ready yet")
            return
        }

        val points = recentMarkersMap[id]
        if (points.isNullOrEmpty()) {
            Log.d("MapDrawing", "No points found for ID: $id")
            return
        }

        Log.d("MapDrawing", "Drawing ${points.size} points for ID: $id")

        // Convert points to LatLng list
        val color = getColorForId(id)
        val latLngPoints = points.map { it.point }

        latLngPoints.firstOrNull()?.let { firstPoint ->
            googleMap.addMarker(
                MarkerOptions()
                    .position(firstPoint)
                    .title("Last Position")
                    .snippet("ID: $id")
                    .icon(BitmapDescriptorFactory.defaultMarker(getHueFromColor(color)))
            )
        }

        // Draw the polyline
        if (latLngPoints.size >= 2) {
            val polylineOptions = PolylineOptions()
                .addAll(latLngPoints)
                .color(color)
                .width(5f)
                .geodesic(true)

            googleMap.addPolyline(polylineOptions)
        }
    }

    private fun drawForStudentId(id: Int) {
        addMarkersForStudent(id)
        drawPolylineForStudent(id)
    }

    private fun drawRecentMarkers() {
        if (!isMapReady) {
            Log.d("MapDrawing", "Map not ready in drawRecentMarkers")
            return
        }

        googleMap.clear() // Clear existing markers and polylines

        val ids = databaseHelper.getAllIds()
        Log.d("MapDrawing", "Found ${ids.size} IDs to draw")

        for (id in ids) {
            Log.d("MapDrawing", "Processing ID: $id")
            addRecentMarkersForStudent(id)
            drawPolylineForRecent(id)
        }

        fitCameraToPoints()
    }

    private fun processPayload(payload: String?) {
        payload?.let {
            try {
                val parts = payload.split(", ").map { it.split(": ").last() }
                if (parts.size == 4) {
                    val id = parts[0].toInt()
                    val latitude = parts[1].toDouble()
                    val longitude = parts[2].toDouble()
                    val timestamp = parts[3].toLong()

                    Log.d("ProcessPayload", "Attempting to insert: ID=$id, Lat=$latitude, Long=$longitude")

                    if (databaseHelper.insertData(id, latitude, longitude, timestamp)) {
                        Log.d("ProcessPayload", "Data inserted successfully")
                        updateStudentSpeed(id)

                        // Update UI on main thread
                        runOnUiThread {
                            if (isMapReady) {
                                drawRecentMarkers()
                                updateStudentSpeed(id)
                            }
                        }
                    } else {
                        Log.e("ProcessPayload", "Failed to insert data")
                    }
                } else {
                    Log.e("ProcessPayload", "Invalid payload format: $payload")
                }
            } catch (e: Exception) {
                Log.e("ProcessPayload", "Error processing payload", e)
            }
        }
    }

    private fun populateStudentData() {
        val allIds = databaseHelper.getAllIds()
        Log.d("StudentData", "Found ${allIds.size} student IDs")

        // For each ID, retrieve the location data and calculate speeds
        for (id in allIds) {
            val locationData = databaseHelper.getLocationDataById(id)
            if (locationData.isNotEmpty()) {
                val speedData = calculateSpeedStatistics(locationData)
                val student = StudentInfo(id, speedData)
                studentData.add(student)
            }
        }
    }

    private fun calculateSpeedStatistics(locationData: List<LocationData>): SpeedData {
        var minSpeed = Double.MAX_VALUE
        var maxSpeed = Double.MIN_VALUE
        var totalSpeed = 0.0
        var count = 0

        for (data in locationData) {
            val speed = data.speed
            minSpeed = minOf(minSpeed, speed)
            maxSpeed = maxOf(maxSpeed, speed)
            totalSpeed += speed
            count++
        }

        val avgSpeed = if (count > 0) totalSpeed / count else 0.0
        return SpeedData(minSpeed, maxSpeed, avgSpeed)
    }

    private fun updateStudentSpeed(id: Int) {
        val student = studentData.find { it.id == id }
        student?.let {
            val minSpeed = it.speedData.minSpeed
            val maxSpeed = it.speedData.maxSpeed
            val avgSpeed = it.speedData.avgSpeed

            val speedText = "Min: $minSpeed, Max: $maxSpeed, Avg: $avgSpeed"
            val studentIdText = "Student ID: $id"

            runOnUiThread {
                findViewById<TextView>(R.id.studentIdText).text = studentIdText
                findViewById<TextView>(R.id.speedStatsText).text = speedText
            }
        }
    }

    private fun fitCameraToPoints() {
        // Function to adjust the camera position based on all points drawn
        val boundsBuilder = LatLngBounds.Builder()

        markersMap.values.flatten().forEach { boundsBuilder.include(it.point) }
        recentMarkersMap.values.flatten().forEach { boundsBuilder.include(it.point) }

        val bounds = boundsBuilder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    // Placeholder function
    private fun setupStudentAdapter() {
        studentAdapter = StudentAdapter(studentData, this)
    }

    private fun setupDateRangeListeners() {
        val dateInput = findViewById<EditText>(R.id.dateInput)
        val fromDateInput = findViewById<EditText>(R.id.fromDateInput)
        val toDateInput = findViewById<EditText>(R.id.toDateInput)

        dateInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val dateStr = s?.toString()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                try {
                    val date = sdf.parse(dateStr)
                    date?.let {
                        // filter dates or data based on this date range
                    }
                } catch (e: ParseException) {
                    Toast.makeText(applicationContext, "Invalid date format", Toast.LENGTH_SHORT).show()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })



    private fun setupStudentAdapter() {
        val rvStudentList: RecyclerView = findViewById(R.id.rvStudents)

        Log.d("RecyclerView", "Initial student list size: ${studentInfo.size}")

        studentListAdapter = StudentAdapter(studentInfo, this)
        rvStudentList.apply {
            adapter = studentListAdapter
            layoutManager = LinearLayoutManager(this@MapActivity)
        }
    }

    private fun fitCameraToPoints() {
        if (!mapReady || recentPointsMap.isEmpty()) {
            return
        }

        // Collect all points from all IDs
        val allPoints = recentPointsMap.values.flatMap { it }

        if (allPoints.isEmpty()) {
            return
        }

        // Build bounds to include all points
        val boundsBuilder = LatLngBounds.Builder()
        allPoints.forEach { markerPoint ->
            boundsBuilder.include(markerPoint.point)
        }

        try {
            // Create bounds that include all points
            val bounds = boundsBuilder.build()

            // Animate camera to show all points with some padding
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,  // The bounds to show
                    100     // Padding in pixels
                )
            )
        } catch (e: IllegalStateException) {
            Log.e("MapCamera", "Error fitting camera to points", e)
        }
    }

    private fun testpoints(){
        dbHelper.deleteDb()
        val now = System.currentTimeMillis() / 1000
        val then = now - 240
        val between = now - 120
        processPayload("ID: 816033593, LAT: 10.640947, LONG: -61.402638, TIMESTAMP: $now")
        processPayload("ID: 816033593, LAT: 10.639731, LONG: -61.402749, TIMESTAMP: $then")
        processPayload("ID: 816033593, LAT: 10.640192, LONG: -61.402694, TIMESTAMP: $between")
        processPayload("ID: 816031872, LAT: 10.637610, LONG: -61.400561, TIMESTAMP: $now")
        processPayload("ID: 816031872, LAT: 10.638536, LONG: -61.399385, TIMESTAMP: $then")

        dbHelper.logAllData()
    }

    fun getColorForId(id: Int): Int {
        // Hash the ID to get a consistent value
        val hash = id.hashCode()

        // Convert the hash value to a color
        val red = (hash shr 16) and 0xFF // Extract the red component from the hash
        val green = (hash shr 8) and 0xFF // Extract the green component from the hash
        val blue = hash and 0xFF // Extract the blue component from the hash

        // Return the color with full alpha (255)
        return Color.argb(255, red, green, blue)
    }

    fun getHueFromColor(color: Int): Float {
        // Convert the color to HSV
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        // Extract the Hue (first value in the HSV array)
        return hsv[0]
    }





}

data class StudentInfo(val id: Int, var maxSpd: Double, var minSpd: Double, var avgSpd: Double, var totalEntries: Int) {

}

// Data class to represent a custom marker point
data class CustomMarkerPoints(val id: Int, val point: LatLng, val time: Long)
