package com.unfallenmore.quickjokes.data.repository

import com.unfallenmore.quickjokes.data.local.dao.TopicDao
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.model.CoinTransactionType
import com.unfallenmore.quickjokes.data.model.TopicType
import com.unfallenmore.quickjokes.domain.TopicIds
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Темы: наблюдение, CRUD пользовательских и разблокировка за монеты.
 * Разблокировка списывает монеты и пишет реестр транзакций атомарно
 * через [CoinLedger].
 */
@Singleton
class TopicRepository @Inject constructor(
    private val topicDao: TopicDao,
    private val ledger: CoinLedger,
    private val imageStore: TopicImageStore,
) {
    fun observeAll(): Flow<List<Topic>> = topicDao.observeAll()

    fun observeById(id: Long): Flow<Topic?> = topicDao.observeById(id)

    suspend fun getById(id: Long): Topic? = topicDao.getById(id)

    suspend fun getCustomCount(): Int = topicDao.countCustom()

    /**
     * Создать собственную тему.
     * @return id новой темы или null, если свободных слотов нет.
     */
    suspend fun createCustomTopic(
        title: String,
        description: String,
        type: TopicType,
        imagePath: String? = null,
        situation: String? = null,
    ): Long? {
        if (remainingCustomSlots() <= 0) return null

        val topic = Topic(
            id = TopicIds.nextCustomId(topicDao.maxId()),
            title = title,
            description = description,
            type = type,
            imagePath = imagePath,
            situation = situation,
            isCustom = true,
            isLocked = false,
            cost = 0,
        )
        return topicDao.insert(topic)
    }

    suspend fun updateTopic(topic: Topic) = topicDao.update(topic)

    /** Удаление темы: вместе с ней уходит и копия её картинки. */
    suspend fun deleteTopic(topicId: Long) {
        val topic = topicDao.getById(topicId)
        topicDao.deleteById(topicId)
        if (topic != null) imageStore.delete(topic.imagePath)
    }

    /** Покупка разблокировки темы. false — не хватает монет или тема уже открыта. */
    suspend fun unlockTopic(topicId: Long): Boolean {
        val topic = topicDao.getById(topicId) ?: return false
        if (!topic.isLocked) return false
        if (topic.cost <= 0) {
            topicDao.unlock(topicId)
            ledger.inTransaction {
                ledger.updateProfileInTransaction { p ->
                    p.copy(topicsUnlocked = p.topicsUnlocked + 1)
                }
            }
            return true
        }

        return ledger.inTransaction {
            val ok = ledger.spendInTransaction(
                amount = topic.cost,
                type = CoinTransactionType.TOPIC_UNLOCK,
                description = "Разблокировка темы «${topic.title}»",
            )
            if (ok) {
                topicDao.unlock(topicId)
                ledger.updateProfileInTransaction { p ->
                    p.copy(topicsUnlocked = p.topicsUnlocked + 1)
                }
            }
            ok
        }
    }

    /** Сколько слотов доступно для новых собственных тем. */
    suspend fun remainingCustomSlots(): Int {
        val profile = ledger.getOrCreateProfile()
        return (profile.customTopicSlots - topicDao.countCustom()).coerceAtLeast(0)
    }

    /** Покупка дополнительного слота под свою тему. */
    suspend fun buyCustomSlot(): Boolean = ledger.inTransaction {
        val ok = ledger.spendInTransaction(
            amount = CUSTOM_SLOT_COST,
            type = CoinTransactionType.CUSTOM_TOPIC_PURCHASE,
            description = "Слот для своей темы",
        )
        if (ok) {
            ledger.updateProfileInTransaction { p ->
                p.copy(customTopicSlots = p.customTopicSlots + 1)
            }
        }
        ok
    }

    companion object {
        const val CUSTOM_SLOT_COST = 100
    }
}
