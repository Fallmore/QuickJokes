package com.unfallenmore.quickjokes.data.backup

import com.unfallenmore.quickjokes.data.local.entity.CoinTransaction
import com.unfallenmore.quickjokes.data.local.entity.Joke
import com.unfallenmore.quickjokes.data.local.entity.LoginDay
import com.unfallenmore.quickjokes.data.local.entity.Session
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.local.entity.UserAchievement
import com.unfallenmore.quickjokes.data.local.entity.UserProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Снимок всех пользовательских данных для экспорта/импорта.
 *
 * Картинки собственных тем выгруживаются как base64 ([topicImages]:
 * относительный путь → base64), чтобы бэкап был самодостаточным.
 */
data class BackupData(
    val profile: UserProfile,
    val loginDays: List<LoginDay>,
    val unlockedAchievements: List<UserAchievement>,
    val transactions: List<CoinTransaction>,
    val customTopics: List<Topic>,
    val sessions: List<Session>,
    val jokes: List<Joke>,
    val topicImages: Map<String, String> = emptyMap(),
    /** Голосовые записи шуток: относительный путь → base64. */
    val jokeAudios: Map<String, String> = emptyMap(),
)

/**
 * JSON-формат бэкапа: {format, version, profile, loginDays, achievements,
 * transactions, topics, sessions, jokes, topicImages, jokeAudios}.
 *
 * Чистый код без Android (org.json) — кодек проверяется юнит-тестами.
 * Незнакомые значения enum'ов при импорте деградируют до значений по
 * умолчанию (fromString), поэтому старый бэкап не ломает новый импорт;
 * отсутствующие поля (jokeAudios в старых файлах) трактуются как пустые.
 */
object BackupCodec {

    const val FORMAT = "quickjokes-backup"
    const val VERSION = 1

    fun encode(data: BackupData): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("profile", encodeProfile(data.profile))
            .put("loginDays", data.loginDays.toJsonArray(::encodeLoginDay))
            .put("achievements", data.unlockedAchievements.toJsonArray(::encodeUnlocked))
            .put("transactions", data.transactions.toJsonArray(::encodeTransaction))
            .put("topics", data.customTopics.toJsonArray(::encodeTopic))
            .put("sessions", data.sessions.toJsonArray(::encodeSession))
            .put("jokes", data.jokes.toJsonArray(::encodeJoke))

        val images = JSONObject()
        data.topicImages.forEach { (path, base64) -> images.put(path, base64) }
        root.put("topicImages", images)

        val audios = JSONObject()
        data.jokeAudios.forEach { (path, base64) -> audios.put(path, base64) }
        root.put("jokeAudios", audios)

        return root.toString(2)
    }

    /** @throws IllegalArgumentException если это не бэкап или версия новее поддерживаемой. */
    fun decode(json: String): BackupData {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("Файл не похож на бэкап QuickJokes") }

        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("Файл не похож на бэкап QuickJokes")
        }
        val version = root.optInt("version", -1)
        if (version !in 1..VERSION) {
            throw IllegalArgumentException(
                "Версия файла ($version) не поддерживается этой версией приложения",
            )
        }

        val images = root.optJSONObject("topicImages") ?: JSONObject()
        val imageKeys = images.keys().asSequence().toList()

        // Старые бэкапы (до записей речи) поля не содержат — отдаём пустую карту
        val audios = root.optJSONObject("jokeAudios") ?: JSONObject()
        val audioKeys = audios.keys().asSequence().toList()

        return BackupData(
            profile = decodeProfile(root.optJSONObject("profile") ?: JSONObject()),
            loginDays = root.array("loginDays").mapObjects(::decodeLoginDay),
            unlockedAchievements = root.array("achievements").mapObjects(::decodeUnlocked),
            transactions = root.array("transactions").mapObjects(::decodeTransaction),
            customTopics = root.array("topics").mapObjects(::decodeTopic),
            sessions = root.array("sessions").mapObjects(::decodeSession),
            jokes = root.array("jokes").mapObjects(::decodeJoke),
            topicImages = imageKeys.associateWith { images.optString(it, "") },
            jokeAudios = audioKeys.associateWith { audios.optString(it, "") },
        )
    }

    // --- Профиль ---

    private fun encodeProfile(p: UserProfile) = JSONObject()
        .put("id", p.id)
        .put("coins", p.coins)
        .put("lastBonusAt", p.lastBonusAt)
        .put("lastLoginDate", p.lastLoginDate)
        .put("streak", p.streak)
        .put("totalJokes", p.totalJokes)
        .put("customTopicSlots", p.customTopicSlots)
        .put("topicsUnlocked", p.topicsUnlocked)
        .put("onboardingDone", p.onboardingDone)

    private fun decodeProfile(o: JSONObject) = UserProfile(
        // Профиль всегда единственный: id из файла не доверяем
        id = UserProfile.SINGLE_ID,
        coins = o.optInt("coins"),
        lastBonusAt = o.optLong("lastBonusAt"),
        lastLoginDate = o.optString("lastLoginDate", ""),
        streak = o.optInt("streak"),
        totalJokes = o.optInt("totalJokes"),
        customTopicSlots = o.optInt("customTopicSlots", 1),
        topicsUnlocked = o.optInt("topicsUnlocked"),
        onboardingDone = o.optBoolean("onboardingDone"),
    )

    // --- Входы / достижения / монеты ---

    private fun encodeLoginDay(d: LoginDay) = JSONObject()
        .put("date", d.date)
        .put("bonusCoins", d.bonusCoins)
        .put("streakAtDay", d.streakAtDay)

    private fun decodeLoginDay(o: JSONObject) = LoginDay(
        date = o.optString("date", ""),
        bonusCoins = o.optInt("bonusCoins"),
        streakAtDay = o.optInt("streakAtDay"),
    )

    private fun encodeUnlocked(a: UserAchievement) = JSONObject()
        .put("achievementCode", a.achievementCode)
        .put("unlockedAt", a.unlockedAt)

    private fun decodeUnlocked(o: JSONObject) = UserAchievement(
        achievementCode = o.optString("achievementCode", ""),
        unlockedAt = o.optLong("unlockedAt"),
    )

    private fun encodeTransaction(t: CoinTransaction) = JSONObject()
        .put("id", t.id)
        .put("amount", t.amount)
        .put("type", t.type.name)
        .put("description", t.description)
        .put("timestamp", t.timestamp)

    private fun decodeTransaction(o: JSONObject) = CoinTransaction(
        id = o.optLong("id"),
        amount = o.optInt("amount"),
        type = com.unfallenmore.quickjokes.data.model.CoinTransactionType
            .fromString(o.optString("type")),
        description = o.optString("description", ""),
        timestamp = o.optLong("timestamp"),
    )

    // --- Темы ---

    private fun encodeTopic(t: Topic) = JSONObject()
        .put("id", t.id)
        .put("title", t.title)
        .put("description", t.description)
        .put("type", t.type.name)
        .putNullable("imagePath", t.imagePath)
        .putNullable("situation", t.situation)
        .put("isCustom", t.isCustom)
        .put("isLocked", t.isLocked)
        .put("cost", t.cost)
        .put("createdAt", t.createdAt)

    private fun decodeTopic(o: JSONObject) = Topic(
        id = o.optLong("id"),
        title = o.optString("title", ""),
        description = o.optString("description", ""),
        type = com.unfallenmore.quickjokes.data.model.TopicType.fromString(o.optString("type")),
        imagePath = o.nullableString("imagePath"),
        situation = o.nullableString("situation"),
        isCustom = o.optBoolean("isCustom", true),
        isLocked = o.optBoolean("isLocked"),
        cost = o.optInt("cost"),
        createdAt = o.optLong("createdAt"),
    )

    // --- Сессии / шутки ---

    private fun encodeSession(s: Session) = JSONObject()
        .put("id", s.id)
        .put("topicId", s.topicId)
        .put("startTime", s.startTime)
        .put("endTime", s.endTime)
        .put("durationSec", s.durationSec)
        .put("difficulty", s.difficulty.name)
        .put("inputMode", s.inputMode.name)
        .put("status", s.status.name)
        .put("totalJokes", s.totalJokes)
        .put("jokesPerMinute", s.jokesPerMinute.takeIf { it.isFinite() } ?: 0.0)
        .put("coinsEarned", s.coinsEarned)

    private fun decodeSession(o: JSONObject) = Session(
        id = o.optLong("id"),
        topicId = o.optLong("topicId"),
        startTime = o.optLong("startTime"),
        endTime = o.optLong("endTime"),
        durationSec = o.optInt("durationSec"),
        difficulty = com.unfallenmore.quickjokes.data.model.Difficulty
            .fromString(o.optString("difficulty")),
        inputMode = com.unfallenmore.quickjokes.data.model.InputMode
            .fromString(o.optString("inputMode")),
        status = com.unfallenmore.quickjokes.data.model.SessionStatus
            .fromString(o.optString("status")),
        totalJokes = o.optInt("totalJokes"),
        jokesPerMinute = o.optDouble("jokesPerMinute", 0.0)
            .takeIf { it.isFinite() } ?: 0.0,
        coinsEarned = o.optInt("coinsEarned"),
    )

    private fun encodeJoke(j: Joke) = JSONObject()
        .put("id", j.id)
        .put("sessionId", j.sessionId)
        .put("text", j.text)
        .put("createdAt", j.createdAt)
        .put("orderIndex", j.orderIndex)
        .putNullable("audioPath", j.audioPath)

    private fun decodeJoke(o: JSONObject) = Joke(
        id = o.optLong("id"),
        sessionId = o.optLong("sessionId"),
        text = o.optString("text", ""),
        createdAt = o.optLong("createdAt"),
        orderIndex = o.optInt("orderIndex"),
        audioPath = o.nullableString("audioPath"),
    )

    // --- Общие помощники ---

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key)

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.array(key: String): JSONArray =
        optJSONArray(key) ?: JSONArray()

    private fun <T> List<T>.toJsonArray(encode: (T) -> JSONObject): JSONArray =
        JSONArray().also { arr -> forEach { arr.put(encode(it)) } }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
