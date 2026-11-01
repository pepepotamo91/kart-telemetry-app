package com.ander.karttelemetry.core

import android.content.Context
import kotlin.math.PI

/**
 * Fixed part of the drivetrain after the CVT: CVT output shaft → pinion →
 * chain → sprocket on the rear axle → tyre.
 */
data class Gearing(
    val pinionTeeth: Int = 10,
    val sprocketTeeth: Int = 60,
    val tireDiameterMm: Double = 280.0,
    /** False until the user has saved their own values at least once. */
    val configured: Boolean = false,
) {
    val finalRatio: Double get() = sprocketTeeth.toDouble() / pinionTeeth

    /**
     * CVT ratio (engine rpm ÷ CVT output rpm) from measured engine rpm and GPS
     * speed, or null when it can't be meaningful: too slow (the centrifugal
     * clutch may be slipping) or no rpm reading.
     */
    fun cvtRatio(engineRpm: Double?, speedKmh: Double): Double? {
        if (engineRpm == null || speedKmh < MIN_SPEED_KMH || engineRpm < MIN_RPM) return null
        val wheelRpm = speedKmh / 3.6 / (PI * tireDiameterMm / 1000.0) * 60.0
        val cvtOutputRpm = wheelRpm * finalRatio
        val ratio = engineRpm / cvtOutputRpm
        return ratio.takeIf { it in 0.3..6.0 }
    }

    companion object {
        const val MIN_SPEED_KMH = 8.0
        const val MIN_RPM = 1500.0
    }
}

/** Persists [Gearing] in SharedPreferences. */
class GearingStore(context: Context) {
    private val prefs = context.getSharedPreferences("gearing", Context.MODE_PRIVATE)

    fun load(): Gearing = Gearing(
        pinionTeeth = prefs.getInt(KEY_PINION, Gearing().pinionTeeth),
        sprocketTeeth = prefs.getInt(KEY_SPROCKET, Gearing().sprocketTeeth),
        tireDiameterMm = prefs.getFloat(KEY_TIRE, Gearing().tireDiameterMm.toFloat()).toDouble(),
        configured = prefs.getBoolean(KEY_CONFIGURED, false),
    )

    fun save(gearing: Gearing) {
        prefs.edit()
            .putInt(KEY_PINION, gearing.pinionTeeth)
            .putInt(KEY_SPROCKET, gearing.sprocketTeeth)
            .putFloat(KEY_TIRE, gearing.tireDiameterMm.toFloat())
            .putBoolean(KEY_CONFIGURED, true)
            .apply()
    }

    private companion object {
        const val KEY_PINION = "pinion_teeth"
        const val KEY_SPROCKET = "sprocket_teeth"
        const val KEY_TIRE = "tire_diameter_mm"
        const val KEY_CONFIGURED = "configured"
    }
}
