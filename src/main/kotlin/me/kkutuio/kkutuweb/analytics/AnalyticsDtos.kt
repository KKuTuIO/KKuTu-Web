package me.kkutuio.kkutuweb.analytics

/** `GET /api/admin/analytics/overview` 의 응답. 필드 이름은 백오피스와의 약속이다. */
data class AnalyticsOverview(
    val rangeDays: Int,
    val kpis: AnalyticsKpis,
    val channels: List<AnalyticsChannel>,
    val dailyMetrics: List<AnalyticsDailyMetric>,
    val economyBreakdown: AnalyticsEconomyBreakdown,
    val topItems: List<AnalyticsTopItem>
)

data class AnalyticsKpis(
    val dau: Long,
    /** 어제 대비 증감률(%). 어제 값이 없으면 0. */
    val dauDeltaPct: Double,
    val mau: Long,
    val mauDeltaPct: Double,
    /** DAU / MAU × 100 (0.0~100.0, 소수 한 자리). */
    val stickinessPct: Double,
    val ccu: Long,
    val newUsersToday: Long,
    val returningUsersToday: Long,
    val pingIssuedToday: Long,
    val pingSunkToday: Long,
    val shopPurchasesToday: Long,
    val pendingReports: Long,
    val openTickets: Long,
    val urgentTickets: Long,
    val activeEvents: Long
)

data class AnalyticsChannel(val channelId: Int, val name: String, val status: String, val ccu: Int, val rooms: Int)

data class AnalyticsDailyMetric(
    /** `YYYY-MM-DD` (한국 시간). */
    val date: String,
    val dau: Long,
    val mau: Long,
    val newUsers: Long,
    val returningUsers: Long,
    val peakCcu: Long,
    val pingIssued: Long,
    val pingSunk: Long,
    /** 상점 구매에 쓴 핑. */
    val shopVolume: Long
)

data class AnalyticsEconomyBreakdown(val inflow: List<AnalyticsEconomyEntry>, val sink: List<AnalyticsEconomyEntry>)

data class AnalyticsEconomyEntry(val label: String, val amount: Long)

data class AnalyticsTopItem(val id: String, val name: String, val group: String, val cost: Long, val hit: Long, val revenue: Long)
