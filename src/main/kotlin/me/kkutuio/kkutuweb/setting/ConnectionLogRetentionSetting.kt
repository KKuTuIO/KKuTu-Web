package me.kkutuio.kkutuweb.setting

data class ConnectionLogRetentionSetting(
    val enabled: Boolean,
    val months: Long,
    val batchSize: Int,
    val maxBatchesPerRun: Int
)
