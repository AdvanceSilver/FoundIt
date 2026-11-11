
package com.foundit.app

data class Report(
    val id: String,
    val userId: String,
    val title: String,
    val category: String,
    val description: String,
    val location: String,
    val reportType: String,
    val imageUrl: String?,
    val createdAt: String,
    val status: String = "active",
    val statusUpdatedAt: String? = null,

    // Map coordinates
    val latitude: Double? = null,
    val longitude: Double? = null
)
