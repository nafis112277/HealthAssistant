package com.srgroup.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Basic subscription/billing structure for the clinic running this app
 * instance (single-clinic-per-install assumption for now — matches how
 * the rest of the app has no multi-clinic concept yet).
 *
 * This is intentionally NOT a payment integration - no gateway, no card
 * storage, no invoicing engine. It's the minimal structure the spec asks
 * for ("সাবস্ক্রিপশন/বিলিং বেসিক স্ট্রাকচার"): a plan, a status, and
 * dates, so the admin panel has something real to show/edit. Wiring an
 * actual payment provider (bKash/Nagad/SSLCommerz are the common choices
 * in Bangladesh) is a separate, later piece of work.
 *
 * Always a single row (id = SINGLETON_ID) since there's one clinic.
 */
@Entity(tableName = "clinic_subscription")
data class ClinicSubscription(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val planName: String = "Trial",           // "Trial" | "Basic" | "Pro" | ...
    val status: String = "trial",             // "trial" | "active" | "past_due" | "cancelled"
    val monthlyFeeBdt: Int = 0,
    val startedAtEpochMillis: Long = System.currentTimeMillis(),
    val nextBillingAtEpochMillis: Long? = null,
    val patientCap: Int = 25,                 // plan-level limit, purely informational for now
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
