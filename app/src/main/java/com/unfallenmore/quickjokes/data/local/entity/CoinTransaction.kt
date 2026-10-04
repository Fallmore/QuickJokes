package com.unfallenmore.quickjokes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.unfallenmore.quickjokes.data.model.CoinTransactionType

/**
 * Движение монет — источник истины для баланса.
 * Баланс = SUM(amount) по всем транзакциям (кэш хранится в [UserProfile.coins],
 * обновляется в той же транзакции, что и запись).
 */
@Entity(tableName = "coin_transactions")
data class CoinTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Положительное — начисление, отрицательное — трата. */
    val amount: Int,
    val type: CoinTransactionType,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
)
