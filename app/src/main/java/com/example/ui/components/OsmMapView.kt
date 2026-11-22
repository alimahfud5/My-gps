package com.example.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.MapLayerStyle
import com.example.location.GeoPointData
import com.example.location.MockRunStatus
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.sin

private object GeoJoyTileSources {
    val cartoVoyager: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "CartoVoyager",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$x/$y$mImageFilenameEnding"
        }
    }

    val cartoDark: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "CartoDark",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/dark_all/",
            "https://b.basemaps.cartocdn.com/rastertiles/dark_all/",
            "https://c.basemaps.cartocdn.com/rastertiles/dark_all/"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$x/$y$mImageFilenameEnding"
        }
    }

    val esriSatellite: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        0,
        18,
        256,
        "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }

    val esriTerrain: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldTopo",
        0,
        18,
        256,
        "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }
}

private class LocationPuckOverlay : Overlay() {
    var currentPoint: GeoPoint = GeoPoint(-6.175392, 106.827153)
    var bearing: Float = 0f
    var status: MockRunStatus = MockRunStatus.IDLE
    var isMoving: Boolean = false
    var tappedTarget: GeoPoint? = null

    private val screenPoint = Point()
    private val targetScreenPoint = Point()

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val haloStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val whiteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 3f, 0x55000000)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val targetPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEA4335.toInt()
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 3f, 0x55000000)
    }
    private val targetInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF961B12.toInt()
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        // Draw tapped target pin if present
        tappedTarget?.let { target ->
            mapView.projection.toPixels(target, targetScreenPoint)
            val tx = targetScreenPoint.x.toFloat()
            val ty = targetScreenPoint.y.toFloat()
            val pinPath = Path().apply {
                moveTo(tx, ty)
                cubicTo(tx - 24f, ty - 28f, tx - 22f, ty - 56f, tx, ty - 56f)
                cubicTo(tx + 22f, ty - 56f, tx + 24f, ty - 28f, tx, ty)
                close()
            }
            canvas.drawPath(pinPath, targetPinPaint)
            canvas.drawCircle(tx, ty - 38f, 8f, targetInnerPaint)
        }

        // Draw Google Maps-style blue location puck
        mapView.projection.toPixels(currentPoint, screenPoint)
        val cx = screenPoint.x.toFloat()
        val cy = screenPoint.y.toFloat()

        val primaryColor = when (status) {
            MockRunStatus.ACTIVE_SYSTEM -> 0xFF0F9D58.toInt() // Emerald active
            MockRunStatus.ACTIVE_PREVIEW -> 0xFF1A73E8.toInt() // Google Blue
            MockRunStatus.IDLE -> 0xFF1A73E8.toInt()
        }
        val haloColor = when (status) {
            MockRunStatus.ACTIVE_SYSTEM -> 0x2E0F9D58
            else -> 0x291A73E8
        }
        val haloStroke = when (status) {
            MockRunStatus.ACTIVE_SYSTEM -> 0x660F9D58
            else -> 0x551A73E8
        }

        val haloRadius = if (status != MockRunStatus.IDLE) 54f else 38f
        haloPaint.color = haloColor
        haloStrokePaint.color = haloStroke
        canvas.drawCircle(cx, cy, haloRadius, haloPaint)
        canvas.drawCircle(cx, cy, haloRadius, haloStrokePaint)

        // Directional heading cone (Google Maps style)
        if (isMoving || status != MockRunStatus.IDLE) {
            val coneRadius = 68f
            val oval = RectF(cx - coneRadius, cy - coneRadius, cx + coneRadius, cy + coneRadius)
            // Canvas 0 deg is East; Android bearing 0 deg is North (-90 in Canvas)
            val startAngle = bearing - 90f - 28f
            conePaint.color = if (status == MockRunStatus.ACTIVE_SYSTEM) 0x550F9D58 else 0x551A73E8
            canvas.drawArc(oval, startAngle, 56f, true, conePaint)
        }

        // Crisp white outer ring + center puck
        canvas.drawCircle(cx, cy, 22f, whiteRingPaint)
        dotPaint.color = primaryColor
        canvas.drawCircle(cx, cy, 15.5f, dotPaint)

        // Inner directional pointer dot when moving
        if (isMoving) {
            val rad = Math.toRadians((bearing - 90f).toDouble())
            val px = cx + (cos(rad) * 10f).toFloat()
            val py = cy + (sin(rad) * 10f).toFloat()
            canvas.drawCircle(px, py, 3.5f, whiteRingPaint)
        }
    }
}

@Composable
fun OsmMapView(
    latitude: Double,
    longitude: Double,
    bearing: Float,
    isMoving: Boolean,
    status: MockRunStatus,
    trail: List<GeoPointData>,
    showTrail: Boolean,
    keepCentered: Boolean,
    layerStyle: MapLayerStyle,
    tappedTarget: GeoPointData?,
    recenterTrigger: Int,
    zoomDeltaTrigger: Pair<Int, Double>,
    onSingleTap: (Double, Double) -> Unit,
    onLongPress: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val puckOverlay = remember { LocationPuckOverlay() }
    val trailPolyline = remember {
        Polyline().apply {
            outlinePaint.color = 0xCC1A73E8.toInt()
            outlinePaint.strokeWidth = 9f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
    }
    var lastHandledRecenter by remember { mutableIntStateOf(0) }
    var lastHandledZoom by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mv = mapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mv?.onResume()
                Lifecycle.Event.ON_PAUSE -> mv?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mv?.onDetach()
        }
    }

    LaunchedEffect(recenterTrigger, mapViewRef) {
        val mv = mapViewRef ?: return@LaunchedEffect
        if (recenterTrigger > lastHandledRecenter) {
            lastHandledRecenter = recenterTrigger
            val targetZoom = mv.zoomLevelDouble.coerceAtLeast(16.2)
            mv.controller.animateTo(GeoPoint(latitude, longitude), targetZoom, 420L)
        }
    }

    LaunchedEffect(zoomDeltaTrigger, mapViewRef) {
        val mv = mapViewRef ?: return@LaunchedEffect
        if (zoomDeltaTrigger.first > lastHandledZoom) {
            lastHandledZoom = zoomDeltaTrigger.first
            val nextZoom = (mv.zoomLevelDouble + zoomDeltaTrigger.second).coerceIn(3.0, 19.0)
            mv.controller.setZoom(nextZoom)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 3.0
                maxZoomLevel = 19.5
                isTilesScaledToDpi = true
                setTileSource(GeoJoyTileSources.cartoVoyager)
                controller.setZoom(16.2)
                controller.setCenter(GeoPoint(latitude, longitude))

                val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let { currentOnSingleTap(it.latitude, it.longitude) }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        p?.let { currentOnLongPress(it.latitude, it.longitude) }
                        return true
                    }
                })

                overlays.add(eventsOverlay)
                overlays.add(trailPolyline)
                overlays.add(puckOverlay)
                mapViewRef = this
            }
        },
        update = { mv ->
            val desiredSource = when (layerStyle) {
                MapLayerStyle.DEFAULT -> GeoJoyTileSources.cartoVoyager
                MapLayerStyle.SATELLITE -> GeoJoyTileSources.esriSatellite
                MapLayerStyle.TERRAIN -> GeoJoyTileSources.esriTerrain
                MapLayerStyle.DARK -> GeoJoyTileSources.cartoDark
            }
            if (mv.tileProvider.tileSource.name() != desiredSource.name()) {
                mv.setTileSource(desiredSource)
            }

            val currentGeo = GeoPoint(latitude, longitude)
            puckOverlay.currentPoint = currentGeo
            puckOverlay.bearing = bearing
            puckOverlay.status = status
            puckOverlay.isMoving = isMoving
            puckOverlay.tappedTarget = tappedTarget?.let { GeoPoint(it.latitude, it.longitude) }

            if (showTrail && trail.size >= 2) {
                trailPolyline.isVisible = true
                trailPolyline.outlinePaint.color = if (status == MockRunStatus.ACTIVE_SYSTEM) {
                    0xCC0F9D58.toInt()
                } else {
                    0xCC1A73E8.toInt()
                }
                trailPolyline.setPoints(trail.map { GeoPoint(it.latitude, it.longitude) })
            } else {
                trailPolyline.isVisible = false
            }

            if (keepCentered && (isMoving || status != MockRunStatus.IDLE)) {
                mv.controller.setCenter(currentGeo)
            }

            mv.invalidate()
        }
    )
}
