package eg.deals.radar.network

import com.google.gson.annotations.SerializedName

/** "App health" block of get_stats (StatsDto.appHealth) - present only for admins. */
data class AppHealthDto(
    @SerializedName("crashes_24h") val crashes24h: Int = 0,
    @SerializedName("crashes_7d") val crashes7d: Int = 0,
    @SerializedName("errors_24h") val errors24h: Int = 0,
    @SerializedName("errors_7d") val errors7d: Int = 0,
    @SerializedName("top_error_areas_7d") val topErrorAreas7d: List<NamedCountDto> = emptyList()
)
