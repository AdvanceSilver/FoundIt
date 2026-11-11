
package com.foundit.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var mapStyle: Style? = null

    private lateinit var mapFrame: FrameLayout
    private lateinit var navigationPanel: LinearLayout
    private lateinit var navigationTitle: TextView
    private lateinit var navigationSummary: TextView
    private lateinit var routeOptionsContainer: LinearLayout
    private lateinit var locationInfo: TextView
    private lateinit var useLocationButton: Button
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var resultsScroll: ScrollView
    private lateinit var resultsContainer: LinearLayout

    private var selectedLocation: LatLng? = null
    private var selectedMarker: Marker? = null
    private var currentLocationMarker: Marker? = null

    private var selectionMode = false
    private var itemDirectionsMode = false

    private var navigationDestination: LatLng? = null
    private var destinationName = "Destination"
    private var destinationAddress = ""

    private var isSearching = false
    private var mapLoaded = false
    private var hasDeviceLocationFix = false
    private var hasUserChosenMapPoint = false
    private var shouldCenterOnFirstLocation = true

    private var locationListener: LocationListener? = null
    private var locationManager: LocationManager? = null
    private var locationUpdatesStarted = false
    private var lastKnownDeviceLocation: Location? = null

    private var routeRequestNumber = 0
    private var routeOptions = mutableListOf<RouteOption>()
    private var selectedRouteIndex = 0

    private var reportsLoadJob: Job? = null
    private val activityScope = CoroutineScope(Dispatchers.Main)

    private lateinit var locationPermissionLauncher:
        ActivityResultLauncher<Array<String>>

    private lateinit var locationSettingsLauncher:
        ActivityResultLauncher<IntentSenderRequest>

    private val markerReports = mutableMapOf<Marker, Report>()

    companion object {
        private const val MAP_STYLE =
            "https://tiles.openfreemap.org/styles/liberty"

        private const val PHOTON_URL =
            "https://photon.komoot.io/api/"

        private const val NOMINATIM_SEARCH_URL =
            "https://nominatim.openstreetmap.org/search"

        private const val OSRM_URL =
            "https://router.project-osrm.org/route/v1/driving/"

        private const val BACKGROUND = 0xFF0B0B12.toInt()
        private const val SURFACE = 0xFF151522.toInt()
        private const val PURPLE = 0xFFA78BFA.toInt()
        private const val PURPLE_DARK = 0xFF7653B8.toInt()
        private const val PURPLE_BORDER = 0xFFAF91F0.toInt()
        private const val MUTED = 0xFFA5A0B4.toInt()
        private const val GREEN = 0xFF55D995.toInt()
        private const val RED = 0xFFFF6969.toInt()
        private const val BLUE = 0xFF268CFF.toInt()

        private const val ROUTE_SOURCE_PREFIX = "foundit-route-source-"
        private const val ROUTE_LAYER_PREFIX = "foundit-route-layer-"
        private const val CURRENT_LOCATION_TITLE = "FoundIt current location"
    }

    private data class PlaceResult(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val category: String = "Place"
    )

    private data class RouteOption(
        val geoJson: String,
        val distanceKm: Double,
        val durationMinutes: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = BACKGROUND
        window.navigationBarColor = BACKGROUND

        locationPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val granted =
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

                if (granted) {
                    checkLocationSettings()
                } else {
                    showLocationMessage(
                        "Location permission was not granted. You can still search manually."
                    )
                }
            }

        locationSettingsLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    startContinuousLocationUpdates()
                } else {
                    showDirectionsMessage(
                        "Enable your phone's location services to use your current location."
                    )
                }
            }

        MapLibre.getInstance(this)

        selectionMode =
            intent.getBooleanExtra("select_location", false)

        itemDirectionsMode =
            intent.getBooleanExtra("item_directions", false)

        val isNavigationMode = itemDirectionsMode

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
        }

        mapFrame = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND)
        }

        root.addView(
            mapFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)

        mapFrame.addView(
            mapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val backButton = TextView(this).apply {
            text = "‹   Back"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(11), dp(18), dp(11))
            background = roundedBackground(SURFACE, PURPLE, 14)
            elevation = dp(8).toFloat()
            setOnClickListener { finish() }
        }

        mapFrame.addView(
            backButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dp(16), dp(12), 0, 0)
            }
        )

        // Keep the Back button below the phone status bar.
        ViewCompat.setOnApplyWindowInsetsListener(mapFrame) { _, insets ->
            val statusBarHeight = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            ).top

            val params =
                backButton.layoutParams as FrameLayout.LayoutParams

            params.topMargin = statusBarHeight + dp(12)
            backButton.layoutParams = params

            insets
        }

        ViewCompat.requestApplyInsets(mapFrame)

        searchInput = EditText(this).apply {
            hint = "Search city, area or landmark"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(14), 0, dp(10), 0)
            background = roundedBackground(SURFACE, PURPLE, 14)

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchPlaces()
                    true
                } else {
                    false
                }
            }
        }

        searchButton = Button(this).apply {
            text = "SEARCH"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            backgroundTintList = null
            stateListAnimator = null
            background = roundedBackground(PURPLE_DARK, PURPLE_BORDER, 14)
            setOnClickListener { searchPlaces() }
        }

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        searchRow.addView(
            searchInput,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        searchRow.addView(
            searchButton,
            LinearLayout.LayoutParams(dp(88), dp(52)).apply {
                marginStart = dp(8)
            }
        )

        mapFrame.addView(
            searchRow,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                gravity = Gravity.TOP
                setMargins(dp(16), dp(112), dp(16), 0)
            }
        )

        locationInfo = TextView(this).apply {
            text = "Finding your current location..."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(SURFACE, PURPLE, 14)
            elevation = dp(8).toFloat()

            visibility = if (selectionMode || isNavigationMode) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        mapFrame.addView(
            locationInfo,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                setMargins(dp(16), dp(174), dp(16), 0)
            }
        )

        resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        resultsScroll = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true
            background = roundedBackground(SURFACE, PURPLE, 14)
            elevation = dp(12).toFloat()
            addView(resultsContainer)
        }

        mapFrame.addView(
            resultsScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(250)
            ).apply {
                gravity = Gravity.TOP
                setMargins(dp(16), dp(174), dp(16), 0)
            }
        )

        useLocationButton = Button(this).apply {
            text = "USE THIS LOCATION"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            backgroundTintList = null
            stateListAnimator = null
            background = roundedBackground(PURPLE_DARK, PURPLE_BORDER, 18)
            isEnabled = false
            alpha = 0.55f

            visibility = if (selectionMode) View.VISIBLE else View.GONE

            setOnClickListener {
                val point = selectedLocation ?: return@setOnClickListener

                setResult(
                    RESULT_OK,
                    Intent().apply {
                        putExtra("latitude", point.latitude)
                        putExtra("longitude", point.longitude)
                    }
                )

                finish()
            }
        }

        mapFrame.addView(
            useLocationButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(dp(20), 0, dp(20), dp(24))
            }
        )

        val attribution = TextView(this).apply {
            text = "© OpenStreetMap contributors"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = roundedBackground(
                SURFACE,
                Color.rgb(65, 60, 85),
                8
            )
        }

        mapFrame.addView(
            attribution,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(
                    dp(12),
                    0,
                    0,
                    if (selectionMode) dp(92) else dp(12)
                )
            }
        )

        navigationPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            setBackgroundColor(SURFACE)
            visibility = if (isNavigationMode) View.VISIBLE else View.GONE
        }

        navigationTitle = TextView(this).apply {
            text = "Directions to item"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        navigationSummary = TextView(this).apply {
            text = "Waiting for your current location..."
            textSize = 13f
            setTextColor(MUTED)
            setPadding(0, dp(6), 0, dp(8))
        }

        routeOptionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val recenterButton = Button(this).apply {
            text = "RECENTER ON MY LOCATION"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            backgroundTintList = null
            stateListAnimator = null
            background = roundedBackground(PURPLE_DARK, PURPLE_BORDER, 12)

            setOnClickListener {
                val location = lastKnownDeviceLocation

                if (location != null) {
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            15.5
                        )
                    )
                } else {
                    Toast.makeText(
                        this@MapActivity,
                        "Waiting for your location...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        navigationPanel.addView(navigationTitle)
        navigationPanel.addView(navigationSummary)
        navigationPanel.addView(routeOptionsContainer)

        navigationPanel.addView(
            recenterButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                topMargin = dp(8)
            }
        )

        root.addView(
            navigationPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        mapView.getMapAsync { map ->
            mapLibreMap = map

            map.setStyle(Style.Builder().fromUri(MAP_STYLE)) { style ->
                mapStyle = style
                mapLoaded = true

                val savedLat = intent.getDoubleExtra(
                    "latitude",
                    Double.NaN
                )

                val savedLon = intent.getDoubleExtra(
                    "longitude",
                    Double.NaN
                )

                val hasSavedPoint =
                    validCoordinates(savedLat, savedLon)

                shouldCenterOnFirstLocation = !hasSavedPoint

                val startPoint = if (hasSavedPoint) {
                    LatLng(savedLat, savedLon)
                } else {
                    LatLng(20.5937, 78.9629)
                }

                map.cameraPosition = CameraPosition.Builder()
                    .target(startPoint)
                    .zoom(if (hasSavedPoint) 15.0 else 4.5)
                    .build()

                when {
                    itemDirectionsMode -> {
                        showItemDestination(map)
                        startLocationFlow()
                    }

                    selectionMode && hasSavedPoint -> {
                        selectedLocation = startPoint
                        setSelectedLocation(map, startPoint)
                        startLocationFlow()
                    }

                    else -> {
                        startLocationFlow()
                    }
                }

                map.setOnMarkerClickListener { marker ->
                    val report = markerReports[marker]

                    if (report != null) {
                        showReportDetails(report)
                        true
                    } else {
                        false
                    }
                }

                if (!selectionMode && !itemDirectionsMode) {
                    loadReportMarkers(map)
                }

                map.addOnMapClickListener { point ->
                    if (selectionMode) {
                        setSelectedLocation(map, point)
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    private fun showItemDestination(map: MapLibreMap) {
        val latitude = intent.getDoubleExtra(
            "item_latitude",
            Double.NaN
        )

        val longitude = intent.getDoubleExtra(
            "item_longitude",
            Double.NaN
        )

        destinationName = intent.getStringExtra("item_title")
            ?.takeIf { it.isNotBlank() }
            ?: "Lost/found item"

        destinationAddress =
            intent.getStringExtra("item_location") ?: ""

        if (!validCoordinates(latitude, longitude)) {
            showDirectionsMessage(
                "This report does not have valid map coordinates, so directions cannot be calculated."
            )

            navigationSummary.text =
                "This report has no saved map location."

            return
        }

        val point = LatLng(latitude, longitude)
        navigationDestination = point

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(point, 15.5)
        )

        map.addMarker(
            MarkerOptions()
                .position(point)
                .title(destinationName)
                .snippet(destinationAddress)
        )

        navigationTitle.text = "Directions to item"
        navigationSummary.text =
            "$destinationName\n$destinationAddress\nWaiting for your current location..."
    }

    private fun validCoordinates(
        latitude: Double,
        longitude: Double
    ): Boolean {
        return latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
    }

    private fun startLocationFlow() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            checkLocationSettings()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkLocationSettings() {
        if (isFinishing || isDestroyed) return

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        )
            .setMinUpdateIntervalMillis(3000L)
            .setWaitForAccurateLocation(false)
            .build()

        val settings = LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .setAlwaysShow(true)
            .build()

        LocationServices.getSettingsClient(this)
            .checkLocationSettings(settings)
            .addOnSuccessListener {
                if (!isFinishing && !isDestroyed) {
                    startContinuousLocationUpdates()
                }
            }
            .addOnFailureListener { exception ->
                if (isFinishing || isDestroyed) {
                    return@addOnFailureListener
                }

                if (exception is ResolvableApiException) {
                    try {
                        locationSettingsLauncher.launch(
                            IntentSenderRequest.Builder(
                                exception.resolution
                            ).build()
                        )
                    } catch (_: Exception) {
                        showDirectionsMessage(
                            "Unable to open location settings. Please enable location services."
                        )
                    }
                } else {
                    showDirectionsMessage(
                        "Location services are unavailable. Please check your phone settings."
                    )
                }
            }
    }

    private fun startContinuousLocationUpdates() {
        if (locationUpdatesStarted ||
            isFinishing ||
            isDestroyed
        ) return

        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return

        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationManager = manager

        val listener = object : LocationListener {

            override fun onLocationChanged(location: Location) {
                if (!validCoordinates(
                        location.latitude,
                        location.longitude
                    )
                ) return

                lastKnownDeviceLocation = location
                updateCurrentLocationMarker(location)

                if (itemDirectionsMode) {
                    val destination = navigationDestination

                    if (destination != null && routeOptions.isEmpty()) {
                        requestRoutes(
                            LatLng(
                                location.latitude,
                                location.longitude
                            ),
                            destination
                        )
                    }
                }
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?
            ) {}
        }

        locationListener = listener

        try {
            val providers = manager.getProviders(true)

            providers.forEach { provider ->
                try {
                    if (provider == LocationManager.GPS_PROVIDER &&
                        !fineGranted
                    ) {
                        return@forEach
                    }

                    manager.requestLocationUpdates(
                        provider,
                        5000L,
                        5f,
                        listener,
                        Looper.getMainLooper()
                    )

                    val last = manager.getLastKnownLocation(provider)

                    if (last != null &&
                        (
                            lastKnownDeviceLocation == null ||
                            last.time >
                            (lastKnownDeviceLocation?.time ?: 0L)
                        )
                    ) {
                        lastKnownDeviceLocation = last
                    }
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }

            locationUpdatesStarted = true

            lastKnownDeviceLocation?.let {
                updateCurrentLocationMarker(it)
            }

            if (lastKnownDeviceLocation == null) {
                showLocationMessage(
                    "Finding your current location..."
                )
            }
        } catch (_: SecurityException) {
            showLocationMessage(
                "Location permission is needed to use your current location."
            )
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationListener?.let { listener ->
                locationManager?.removeUpdates(listener)
            }
        } catch (_: Exception) {
        }

        locationListener = null
        locationUpdatesStarted = false
    }

    private fun updateCurrentLocationMarker(location: Location) {
        val map = mapLibreMap ?: return

        val point = LatLng(
            location.latitude,
            location.longitude
        )

        val firstFix = !hasDeviceLocationFix
        hasDeviceLocationFix = true

        if (currentLocationMarker == null) {
            currentLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(point)
                    .title(CURRENT_LOCATION_TITLE)
                    .icon(createBlueLocationIcon())
            )
        } else {
            currentLocationMarker?.position = point
        }

        if (firstFix &&
            shouldCenterOnFirstLocation &&
            !hasUserChosenMapPoint &&
            !itemDirectionsMode
        ) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(point, 14.5)
            )
        }

        if (itemDirectionsMode) {
            val destination = navigationDestination

            if (destination != null &&
                routeOptions.isEmpty() &&
                routeRequestNumber == 0
            ) {
                requestRoutes(point, destination)
            }
        }
    }

    private fun createBlueLocationIcon(): Icon {
        val size = dp(30)

        val bitmap = Bitmap.createBitmap(
            size,
            size,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        canvas.drawCircle(
            size / 2f,
            size / 2f,
            size / 2.1f,
            paint
        )

        paint.color = BLUE
        canvas.drawCircle(
            size / 2f,
            size / 2f,
            size / 2.7f,
            paint
        )

        paint.color = Color.WHITE
        canvas.drawCircle(
            size / 2f,
            size / 2f,
            size / 7f,
            paint
        )

        return IconFactory.getInstance(this).fromBitmap(bitmap)
    }

    private fun requestRoutes(
        origin: LatLng,
        destination: LatLng
    ) {
        val requestId = ++routeRequestNumber

        showDirectionsMessage("Calculating driving routes...")
        navigationSummary.text = "Calculating driving routes..."

        Thread {
            var connection: HttpURLConnection? = null
            val foundRoutes = mutableListOf<RouteOption>()
            var error: String? = null

            try {
                val requestUrl =
                    OSRM_URL +
                    "${origin.longitude},${origin.latitude};" +
                    "${destination.longitude},${destination.latitude}" +
                    "?overview=full&geometries=geojson&alternatives=true&steps=false"

                connection = URL(requestUrl)
                    .openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 25000

                connection.setRequestProperty(
                    "User-Agent",
                    "FoundIt-Android/1.0"
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    throw Exception(
                        "Routing service error: $responseCode"
                    )
                }

                val response = BufferedReader(
                    InputStreamReader(connection.inputStream)
                ).use { it.readText() }

                val json = JSONObject(response)

                if (json.optString("code") != "Ok") {
                    throw Exception(
                        json.optString(
                            "message",
                            "No driving route found."
                        )
                    )
                }

                val routes = json.optJSONArray("routes")
                    ?: throw Exception("No routes were returned.")

                for (index in 0 until routes.length()) {
                    val route = routes.optJSONObject(index) ?: continue
                    val geometry = route.optJSONObject("geometry") ?: continue
                    val coordinates =
                        geometry.optJSONArray("coordinates") ?: continue

                    if (coordinates.length() < 2) continue

                    val validPoints = mutableListOf<String>()

                    for (i in 0 until coordinates.length()) {
                        val coordinate =
                            coordinates.optJSONArray(i) ?: continue

                        if (coordinate.length() < 2) continue

                        val lon = coordinate.optDouble(
                            0,
                            Double.NaN
                        )

                        val lat = coordinate.optDouble(
                            1,
                            Double.NaN
                        )

                        if (validCoordinates(lat, lon)) {
                            validPoints.add("[$lon,$lat]")
                        }
                    }

                    if (validPoints.size < 2) continue

                    val geoJson =
                        """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[${validPoints.joinToString(",")}]}}"""

                    foundRoutes.add(
                        RouteOption(
                            geoJson,
                            route.optDouble("distance", 0.0) / 1000.0,
                            route.optDouble("duration", 0.0) / 60.0
                        )
                    )
                }

                if (foundRoutes.isEmpty()) {
                    throw Exception(
                        "No usable driving route was returned."
                    )
                }
            } catch (e: Exception) {
                error = e.message ?: "Unable to calculate routes."
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                if (isFinishing ||
                    isDestroyed ||
                    requestId != routeRequestNumber
                ) {
                    return@runOnUiThread
                }

                if (foundRoutes.isEmpty()) {
                    routeOptions.clear()
                    routeRequestNumber = 0

                    showDirectionsMessage(
                        "Could not calculate a route: ${error ?: "Please try again."}"
                    )

                    navigationSummary.text =
                        "Route unavailable. Check your internet connection and try again."
                } else {
                    routeOptions = foundRoutes
                    selectedRouteIndex = 0
                    displaySelectedRoute()
                }
            }
        }.start()
    }

    private fun displaySelectedRoute() {
        val style = mapStyle ?: return
        val route = routeOptions.getOrNull(selectedRouteIndex) ?: return

        try {
            for (index in 0 until 5) {
                val layerId = ROUTE_LAYER_PREFIX + index
                val sourceId = ROUTE_SOURCE_PREFIX + index

                if (style.getLayer(layerId) != null) {
                    style.removeLayer(layerId)
                }

                if (style.getSource(sourceId) != null) {
                    style.removeSource(sourceId)
                }
            }

            routeOptions.forEachIndexed { index, option ->
                val sourceId = ROUTE_SOURCE_PREFIX + index
                val layerId = ROUTE_LAYER_PREFIX + index

                style.addSource(
                    GeoJsonSource(sourceId, option.geoJson)
                )

                val isSelected = index == selectedRouteIndex

                val color = if (isSelected) {
                    "#A78BFA"
                } else {
                    "#85818F"
                }

                style.addLayer(
                    LineLayer(layerId, sourceId).withProperties(
                        lineColor(color),
                        lineWidth(if (isSelected) 6f else 3f),
                        lineOpacity(if (isSelected) 0.95f else 0.55f)
                    )
                )
            }

            val distanceText = String.format(
                Locale.getDefault(),
                "%.1f km",
                route.distanceKm
            )

            val durationText =
                formatDuration(route.durationMinutes)

            navigationSummary.text =
                "$destinationName\n$distanceText • $durationText • Driving\n$destinationAddress"

            showDirectionsMessage(
                "Route ready: $distanceText • $durationText. The selected route is shown in purple."
            )

            updateRouteButtons()

            val origin = lastKnownDeviceLocation
            val destination = navigationDestination

            if (origin != null && destination != null) {
                val midpoint = LatLng(
                    (origin.latitude + destination.latitude) / 2.0,
                    (origin.longitude + destination.longitude) / 2.0
                )

                val distance = FloatArray(1)

                Location.distanceBetween(
                    origin.latitude,
                    origin.longitude,
                    destination.latitude,
                    destination.longitude,
                    distance
                )

                val zoom = when {
                    distance[0] < 1000f -> 14.0
                    distance[0] < 5000f -> 12.0
                    distance[0] < 20000f -> 10.0
                    distance[0] < 100000f -> 8.0
                    else -> 5.5
                }

                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(midpoint)
                            .zoom(zoom)
                            .build()
                    ),
                    800
                )
            }
        } catch (e: Exception) {
            showDirectionsMessage(
                "The route was found, but the map could not draw it: ${e.message}"
            )
        }
    }

    private fun updateRouteButtons() {
        routeOptionsContainer.removeAllViews()

        routeOptions.forEachIndexed { index, route ->
            val selected = index == selectedRouteIndex

            val button = Button(this).apply {
                text =
                    "${if (selected) "✓ " else ""}Route ${index + 1}  •  " +
                    "${String.format(Locale.getDefault(), "%.1f km", route.distanceKm)}  •  " +
                    formatDuration(route.durationMinutes)

                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                backgroundTintList = null
                stateListAnimator = null

                background = roundedBackground(
                    if (selected) PURPLE_DARK else BACKGROUND,
                    if (selected) PURPLE else Color.rgb(70, 65, 85),
                    12
                )

                setOnClickListener {
                    selectedRouteIndex = index
                    displaySelectedRoute()
                }
            }

            routeOptionsContainer.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(42)
                ).apply {
                    topMargin = dp(5)
                }
            )
        }

        if (routeOptions.size == 1) {
            val note = TextView(this).apply {
                text = "Only one route was returned by the routing service."
                textSize = 11f
                setTextColor(MUTED)
                setPadding(0, dp(5), 0, 0)
            }

            routeOptionsContainer.addView(note)
        }
    }

    private fun formatDuration(minutes: Double): String {
        if (!minutes.isFinite() || minutes < 0) {
            return "Time unavailable"
        }

        return if (minutes >= 60) {
            val hours = (minutes / 60).toInt()
            val remaining = (minutes % 60).toInt()
            "$hours hr $remaining min"
        } else {
            "${minutes.toInt()} min"
        }
    }

    private fun showDirectionsMessage(message: String) {
        if (::locationInfo.isInitialized) {
            locationInfo.visibility = View.VISIBLE
            locationInfo.text = message
            locationInfo.setTextColor(Color.WHITE)
        }
    }

    private fun showLocationMessage(message: String) {
        if (!::locationInfo.isInitialized) return

        if (!selectionMode && !itemDirectionsMode) {
            return
        }

        locationInfo.visibility = View.VISIBLE
        locationInfo.text = message
        locationInfo.setTextColor(Color.WHITE)
    }

    private fun loadReportMarkers(map: MapLibreMap) {
        reportsLoadJob?.cancel()

        reportsLoadJob = activityScope.launch {
            try {
                val reports =
                    SupabaseApi(this@MapActivity).fetchReports()

                if (isFinishing || isDestroyed) return@launch

                markerReports.clear()

                val iconFactory =
                    IconFactory.getInstance(this@MapActivity)

                var count = 0
                var photoCount = 0

                reports.forEach { report ->
                    val latitude = report.latitude
                    val longitude = report.longitude

                    if (latitude == null ||
                        longitude == null ||
                        !validCoordinates(latitude, longitude)
                    ) {
                        return@forEach
                    }

                    val isLost = report.reportType.trim()
                        .equals("lost", ignoreCase = true)

                    var markerIcon = iconFactory.defaultMarker()

                    if (!report.imageUrl.isNullOrBlank()) {
                        try {
                            val imageSize = dp(72)

                            val bitmap = withContext(Dispatchers.IO) {
                                Glide.with(applicationContext)
                                    .asBitmap()
                                    .load(report.imageUrl)
                                    .submit(imageSize, imageSize)
                                    .get()
                            }

                            if (isFinishing || isDestroyed) {
                                return@launch
                            }

                            markerIcon = iconFactory.fromBitmap(
                                createPhotoMarkerBitmap(
                                    bitmap,
                                    isLost
                                )
                            )

                            photoCount++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            markerIcon = iconFactory.defaultMarker()
                        }
                    }

                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(
                                LatLng(latitude, longitude)
                            )
                            .title(
                                report.title.ifBlank {
                                    "Untitled report"
                                }
                            )
                            .snippet(
                                "${if (isLost) "LOST" else "FOUND"} • ${report.location}"
                            )
                            .icon(markerIcon)
                    )

                    markerReports[marker] = report
                    count++
                }

                Toast.makeText(
                    this@MapActivity,
                    if (count == 0) {
                        "No reports with map locations yet."
                    } else {
                        "$count reports loaded • $photoCount photo markers"
                    },
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@MapActivity,
                        "Could not load reports: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createPhotoMarkerBitmap(
        source: Bitmap,
        isLost: Boolean
    ): Bitmap {
        val size = dp(72)

        val bitmap = Bitmap.createBitmap(
            size,
            size,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        val borderColor = if (isLost) RED else GREEN
        val center = size / 2f
        val radius = size / 2f - dp(3)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = borderColor
        canvas.drawCircle(center, center, radius, paint)

        val photoRadius = radius - dp(5)

        val clipPath = Path().apply {
            addCircle(
                center,
                center,
                photoRadius,
                Path.Direction.CW
            )
        }

        canvas.save()
        canvas.clipPath(clipPath)

        val scale = maxOf(
            size.toFloat() / source.width,
            size.toFloat() / source.height
        )

        val scaledWidth = source.width * scale
        val scaledHeight = source.height * scale

        val destination = RectF(
            (size - scaledWidth) / 2f,
            (size - scaledHeight) / 2f,
            (size + scaledWidth) / 2f,
            (size + scaledHeight) / 2f
        )

        canvas.drawBitmap(
            source,
            null,
            destination,
            paint
        )

        canvas.restore()

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2).toFloat()

        canvas.drawCircle(
            center,
            center,
            photoRadius,
            paint
        )

        return bitmap
    }

    private fun showReportDetails(report: Report) {
        val scrollView = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        val isLost = report.reportType.trim()
            .equals("lost", ignoreCase = true)

        val typeText = TextView(this).apply {
            text = if (isLost) "LOST ITEM" else "FOUND ITEM"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isLost) RED else GREEN)
        }

        content.addView(typeText)

        val titleText = TextView(this).apply {
            text = report.title
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(7), 0, dp(10))
        }

        content.addView(titleText)

        if (!report.imageUrl.isNullOrBlank()) {
            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedBackground(SURFACE, PURPLE, 12)
                clipToOutline = true
            }

            content.addView(
                imageView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(180)
                ).apply {
                    bottomMargin = dp(14)
                }
            )

            Glide.with(this)
                .load(report.imageUrl)
                .centerCrop()
                .into(imageView)
        }

        fun addDetail(label: String, value: String) {
            if (value.isBlank()) return

            content.addView(
                TextView(this).apply {
                    text = "$label\n$value"
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(5), 0, dp(9))
                }
            )
        }

        addDetail("Category", report.category)
        addDetail("Description", report.description)
        addDetail("Location", report.location)

        addDetail(
            "Status",
            report.status.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(Locale.getDefault())
                } else {
                    it.toString()
                }
            }
        )

        scrollView.addView(content)

        val hasCoordinates =
            report.latitude != null &&
            report.longitude != null &&
            validCoordinates(
                report.latitude,
                report.longitude
            )

        val builder = AlertDialog.Builder(this)
            .setView(scrollView)
            .setNegativeButton("CLOSE", null)

        if (hasCoordinates) {
            builder.setPositiveButton("GET DIRECTIONS") { _, _ ->
                openReportDirections(report)
            }
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(
                android.R.color.transparent
            )

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(PURPLE)

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(PURPLE)
        }

        dialog.show()
    }

    private fun openReportDirections(report: Report) {
        val latitude = report.latitude ?: return
        val longitude = report.longitude ?: return

        if (!validCoordinates(latitude, longitude)) {
            Toast.makeText(
                this,
                "This report has no valid map location.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val navigationIntent =
            Intent(this, MapActivity::class.java).apply {
                putExtra("item_directions", true)
                putExtra("item_latitude", latitude)
                putExtra("item_longitude", longitude)
                putExtra("item_title", report.title)
                putExtra("item_location", report.location)
            }

        startActivity(navigationIntent)
    }

    private fun searchPlaces() {
        if (isSearching) return

        val query = searchInput.text.toString().trim()

        if (query.length < 2) {
            Toast.makeText(
                this,
                "Enter at least 2 characters.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        isSearching = true
        searchButton.text = "..."
        searchButton.isEnabled = false

        resultsContainer.removeAllViews()
        resultsScroll.visibility = View.GONE

        val inputManager = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager

        inputManager.hideSoftInputFromWindow(
            searchInput.windowToken,
            0
        )

        val cameraPoint = mapLibreMap?.cameraPosition?.target
        val biasLocation = lastKnownDeviceLocation

        val biasLatitude =
            biasLocation?.latitude ?: cameraPoint?.latitude

        val biasLongitude =
            biasLocation?.longitude ?: cameraPoint?.longitude

        Thread {
            var places = emptyList<PlaceResult>()
            var error: String? = null

            try {
                places = searchPhoton(
                    query,
                    biasLatitude,
                    biasLongitude
                )

                if (places.isEmpty()) {
                    places = searchNominatim(
                        query,
                        biasLatitude,
                        biasLongitude
                    )
                }

                if (places.isEmpty()) {
                    error = "No places found. Try a more specific search."
                }
            } catch (e: Exception) {
                error = e.message ?: "Unable to search right now."
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                isSearching = false
                searchButton.text = "SEARCH"
                searchButton.isEnabled = true

                if (error != null) {
                    Toast.makeText(
                        this,
                        error,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    showSearchResults(places)
                }
            }
        }.start()
    }

    private fun searchPhoton(
        query: String,
        biasLatitude: Double?,
        biasLongitude: Double?
    ): List<PlaceResult> {
        var connection: HttpURLConnection? = null

        try {
            val encoded = URLEncoder.encode(query, "UTF-8")

            val urlBuilder = StringBuilder(
                "${PHOTON_URL}?q=$encoded&limit=12&lang=en"
            )

            if (biasLatitude != null &&
                biasLongitude != null &&
                validCoordinates(biasLatitude, biasLongitude)
            ) {
                urlBuilder.append("&lat=$biasLatitude")
                urlBuilder.append("&lon=$biasLongitude")
            }

            connection = URL(urlBuilder.toString())
                .openConnection() as HttpURLConnection

            connection.connectTimeout = 12000
            connection.readTimeout = 12000

            connection.setRequestProperty(
                "User-Agent",
                "FoundIt-Android/1.0"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            if (connection.responseCode !in 200..299) {
                return emptyList()
            }

            val response = BufferedReader(
                InputStreamReader(connection.inputStream)
            ).use { it.readText() }

            val features =
                JSONObject(response).optJSONArray("features")
                    ?: return emptyList()

            val found = mutableListOf<PlaceResult>()

            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue

                val geometry =
                    feature.optJSONObject("geometry") ?: continue

                val coordinates =
                    geometry.optJSONArray("coordinates") ?: continue

                if (coordinates.length() < 2) continue

                val longitude = coordinates.optDouble(
                    0,
                    Double.NaN
                )

                val latitude = coordinates.optDouble(
                    1,
                    Double.NaN
                )

                if (!validCoordinates(latitude, longitude)) {
                    continue
                }

                val properties =
                    feature.optJSONObject("properties") ?: continue

                val name = properties.optString("name")
                    .takeIf {
                        it.isNotBlank() && it != "null"
                    }
                    ?: continue

                val addressParts = linkedSetOf<String>()

                listOf(
                    "housenumber",
                    "street",
                    "district",
                    "suburb",
                    "city",
                    "county",
                    "state",
                    "postcode",
                    "country"
                ).forEach { key ->
                    val value = properties.optString(key)

                    if (value.isNotBlank() &&
                        value != "null" &&
                        value != name
                    ) {
                        addressParts.add(value)
                    }
                }

                val address = addressParts.joinToString(", ")
                    .ifBlank {
                        "Location selected on map"
                    }

                found.add(
                    PlaceResult(
                        name,
                        address,
                        latitude,
                        longitude,
                        getPhotonCategory(properties)
                    )
                )
            }

            return found.distinctBy {
                "${it.name.lowercase(Locale.ROOT)}_${it.latitude}_${it.longitude}"
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun searchNominatim(
        query: String,
        biasLatitude: Double?,
        biasLongitude: Double?
    ): List<PlaceResult> {
        var connection: HttpURLConnection? = null

        try {
            val encoded = URLEncoder.encode(query, "UTF-8")

            val urlBuilder = StringBuilder(
                "$NOMINATIM_SEARCH_URL" +
                "?format=jsonv2" +
                "&q=$encoded" +
                "&limit=12" +
                "&addressdetails=1" +
                "&namedetails=1" +
                "&countrycodes=in"
            )

            if (biasLatitude != null &&
                biasLongitude != null &&
                validCoordinates(biasLatitude, biasLongitude)
            ) {
                val delta = 0.35

                val left = biasLongitude - delta
                val right = biasLongitude + delta
                val top = biasLatitude + delta
                val bottom = biasLatitude - delta

                urlBuilder.append(
                    "&viewbox=$left,$top,$right,$bottom"
                )

                urlBuilder.append("&bounded=0")
            }

            connection = URL(urlBuilder.toString())
                .openConnection() as HttpURLConnection

            connection.connectTimeout = 12000
            connection.readTimeout = 15000

            connection.setRequestProperty(
                "User-Agent",
                "FoundIt-Android/1.0 (place search)"
            )

            connection.setRequestProperty(
                "Accept-Language",
                "en"
            )

            if (connection.responseCode !in 200..299) {
                return emptyList()
            }

            val response = BufferedReader(
                InputStreamReader(connection.inputStream)
            ).use { it.readText() }

            val results = JSONArray(response)
            val found = mutableListOf<PlaceResult>()

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue

                val latitude = item.optDouble(
                    "lat",
                    Double.NaN
                )

                val longitude = item.optDouble(
                    "lon",
                    Double.NaN
                )

                if (!validCoordinates(latitude, longitude)) {
                    continue
                }

                val namedetails =
                    item.optJSONObject("namedetails")

                val name =
                    namedetails?.optString("name")
                        ?.takeIf {
                            it.isNotBlank() && it != "null"
                        }
                        ?: item.optString("name")
                            .takeIf {
                                it.isNotBlank() && it != "null"
                            }
                        ?: continue

                val addressObject =
                    item.optJSONObject("address")

                val addressParts = linkedSetOf<String>()

                if (addressObject != null) {
                    listOf(
                        "house_number",
                        "road",
                        "pedestrian",
                        "neighbourhood",
                        "suburb",
                        "city",
                        "town",
                        "village",
                        "county",
                        "state",
                        "postcode",
                        "country"
                    ).forEach { key ->
                        val value = addressObject.optString(key)

                        if (value.isNotBlank() &&
                            value != "null" &&
                            value != name
                        ) {
                            addressParts.add(value)
                        }
                    }
                }

                val address = addressParts.joinToString(", ")
                    .ifBlank {
                        item.optString("display_name")
                            .takeIf {
                                it.isNotBlank() &&
                                it != "null"
                            }
                            ?: "Location selected on map"
                    }

                found.add(
                    PlaceResult(
                        name,
                        address,
                        latitude,
                        longitude,
                        getNominatimCategory(item)
                    )
                )
            }

            return found.distinctBy {
                "${it.name.lowercase(Locale.ROOT)}_${it.latitude}_${it.longitude}"
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun getPhotonCategory(
        properties: JSONObject
    ): String {
        val key = properties.optString("osm_key")
        val value = properties.optString("osm_value")
        val type = properties.optString("type")

        return categoryFromTags(key, value, type)
    }

    private fun getNominatimCategory(
        item: JSONObject
    ): String {
        val category = item.optString("category")
        val type = item.optString("type")

        return categoryFromTags(category, type, type)
    }

    private fun categoryFromTags(
        key: String,
        value: String,
        type: String
    ): String {
        val combined = "$key $value $type"
            .lowercase(Locale.ROOT)

        return when {
            combined.contains("bank") ->
                "Bank"

            combined.contains("bus_stop") ||
            combined.contains("bus_station") ||
            combined.contains("platform") ->
                "Bus stop / transport"

            combined.contains("park") ||
            combined.contains("garden") ->
                "Park / garden"

            combined.contains("school") ||
            combined.contains("college") ||
            combined.contains("university") ->
                "School / education"

            combined.contains("police") ->
                "Police station"

            combined.contains("library") ->
                "Public library"

            combined.contains("community_centre") ||
            combined.contains("community centre") ->
                "Community centre"

            combined.contains("government") ||
            combined.contains("townhall") ->
                "Government service"

            combined.contains("railway") ||
            combined.contains("station") ->
                "Transport station"

            combined.contains("hospital") ||
            combined.contains("clinic") ->
                "Healthcare"

            else -> "Place"
        }
    }

    private fun showSearchResults(
        places: List<PlaceResult>
    ) {
        resultsContainer.removeAllViews()
        locationInfo.visibility = View.GONE

        places.forEach { place ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                isClickable = true
                isFocusable = true

                background = roundedBackground(
                    Color.rgb(30, 27, 43),
                    Color.rgb(95, 82, 125),
                    10
                )

                setOnClickListener {
                    chooseSearchResult(place)
                }
            }

            item.addView(
                TextView(this).apply {
                    text = place.name
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                }
            )

            item.addView(
                TextView(this).apply {
                    text = place.category
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(PURPLE)
                    setPadding(0, dp(3), 0, 0)
                }
            )

            if (place.address.isNotBlank()) {
                item.addView(
                    TextView(this).apply {
                        text = place.address
                        textSize = 11f
                        setTextColor(MUTED)
                        setPadding(0, dp(4), 0, 0)
                    }
                )
            }

            resultsContainer.addView(
                item,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(7)
                }
            )
        }

        resultsScroll.visibility = View.VISIBLE
        resultsScroll.bringToFront()
    }

    private fun chooseSearchResult(place: PlaceResult) {
        val map = mapLibreMap ?: return

        if (place.name.isBlank() ||
            !validCoordinates(place.latitude, place.longitude)
        ) {
            Toast.makeText(
                this,
                "This result does not have a valid place name or location.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val point = LatLng(
            place.latitude,
            place.longitude
        )

        hasUserChosenMapPoint = true

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                point,
                15.0
            )
        )

        if (selectionMode) {
            setSelectedLocation(map, point)
        }

        resultsScroll.visibility = View.GONE

        if (selectionMode) {
            locationInfo.visibility = View.VISIBLE
        }

        Toast.makeText(
            this,
            "Selected: ${place.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setSelectedLocation(
        map: MapLibreMap,
        point: LatLng
    ) {
        selectedLocation = point
        hasUserChosenMapPoint = true

        selectedMarker?.let {
            map.removeAnnotation(it)
        }

        selectedMarker = map.addMarker(
            MarkerOptions()
                .position(point)
                .title("Selected item location")
        )

        locationInfo.visibility = View.VISIBLE

        locationInfo.text = String.format(
            Locale.US,
            "Selected location: %.5f, %.5f",
            point.latitude,
            point.longitude
        )

        locationInfo.setTextColor(GREEN)

        useLocationButton.isEnabled = true
        useLocationButton.alpha = 1f
    }

    private fun roundedBackground(
        fillColor: Int,
        borderColor: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), borderColor)
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        if (mapLoaded &&
            itemDirectionsMode &&
            !locationUpdatesStarted
        ) {
            startLocationFlow()
        }
    }

    override fun onPause() {
        stopLocationUpdates()
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        reportsLoadJob?.cancel()
        activityScope.cancel()
        mapView.onDestroy()
        super.onDestroy()
    }
}
