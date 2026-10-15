package com.gunnys.eundunhealth.data.translation

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gunnys.eundunhealth.di.DefaultDispatcher
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val SUPPORTED_VERSION = 2
private val WHITESPACE = Regex("\\s+")
private val SHA_256 = Regex("[0-9a-f]{64}")

@Singleton
class ExerciseTranslationProvider @Inject constructor(
    private val source: ExerciseTranslationSource,
    private val reporter: ExerciseTranslationReporter,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    private val loadMutex = Mutex()

    @Volatile
    private var cachedCatalog: TranslationCatalog? = null

    suspend fun localize(exercise: Exercise): Exercise {
        val catalog = catalog()
        val translation = catalog.exercises[exercise.id]
        val sourceMatches = translation != null &&
            translation.instructions.size == exercise.instructions.size &&
            translation.sourceFingerprint == fingerprint(exercise)

        val name = translation?.name?.takeIf { sourceMatches } ?: exercise.name.also {
            if (catalog.reportMissing) {
                reporter.missing(if (translation == null) "exercise" else "sourceChanged", exercise.id)
            }
        }
        val bodyPart = catalog.bodyParts[normalizeKey(exercise.bodyPart)] ?: exercise.bodyPart.also {
            if (catalog.reportMissing) reporter.missing("bodyPart", exercise.bodyPart)
        }
        val equipment = catalog.equipments[normalizeKey(exercise.equipment)] ?: exercise.equipment.also {
            if (catalog.reportMissing) reporter.missing("equipment", exercise.equipment)
        }
        val instructions = translation?.instructions?.takeIf { sourceMatches } ?: exercise.instructions

        return exercise.copy(
            name = name,
            bodyPart = bodyPart,
            equipment = equipment,
            instructions = instructions,
        )
    }

    @Suppress("RedundantSuspendModifier") // Calls the suspend Exercise overload inside map.
    suspend fun localize(plan: WeeklyPlan): WeeklyPlan = plan.copy(
        days = plan.days.map { day: DayPlan ->
            day.copy(exercises = day.exercises.map { localize(it) })
        },
    )

    @Suppress("TooGenericExceptionCaught") // Any asset I/O/schema failure must publish one safe empty snapshot.
    private suspend fun catalog(): TranslationCatalog {
        cachedCatalog?.let { return it }
        return loadMutex.withLock {
            cachedCatalog?.let { return@withLock it }
            val loaded = try {
                val json = source.load()
                withContext(defaultDispatcher) { parseCatalog(json) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reporter.loadFailed(e)
                TranslationCatalog.EMPTY
            }
            cachedCatalog = loaded
            loaded
        }
    }
}

private data class TranslationCatalog(
    val bodyParts: Map<String, String>,
    val equipments: Map<String, String>,
    val exercises: Map<String, ExerciseTranslation>,
    val reportMissing: Boolean = true,
) {
    companion object {
        val EMPTY = TranslationCatalog(emptyMap(), emptyMap(), emptyMap(), reportMissing = false)
    }
}

private data class ExerciseTranslation(
    val name: String,
    val sourceFingerprint: String,
    val instructions: List<String>,
)

private fun parseCatalog(json: String): TranslationCatalog {
    val root = JsonParser.parseString(json).asJsonObject
    require(root.requiredInt("version") == SUPPORTED_VERSION) { "지원하지 않는 운동 번역 버전입니다." }
    val bodyParts = root.requiredStringMap("bodyParts")
    val equipments = root.requiredStringMap("equipments")
    val exercises = root.requiredObject("exercises").entrySet().associate { (id, value) ->
        require(id.isNotBlank()) { "운동 번역 ID가 비어 있습니다." }
        val item = value.asJsonObject
        val sourceFingerprint = item.requiredString("sourceFingerprint")
        require(sourceFingerprint.matches(SHA_256)) {
            "운동 번역 원문 지문이 SHA-256 형식이 아닙니다: $id"
        }
        id to ExerciseTranslation(
            name = item.requiredString("name"),
            sourceFingerprint = sourceFingerprint,
            instructions = item.requiredStringList("instructions"),
        )
    }
    require(bodyParts.isNotEmpty()) { "운동 부위 번역이 비어 있습니다." }
    require(equipments.isNotEmpty()) { "운동 장비 번역이 비어 있습니다." }
    require(exercises.isNotEmpty()) { "운동 번역이 비어 있습니다." }
    return TranslationCatalog(
        bodyParts = bodyParts,
        equipments = equipments,
        exercises = exercises,
    )
}

private fun JsonObject.requiredObject(name: String): JsonObject = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    ?: error("운동 번역 필드가 없거나 객체가 아닙니다: $name")

private fun JsonObject.requiredInt(name: String): Int = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    ?.asString
    ?.toIntOrNull()
    ?: error("운동 번역 필드가 없거나 숫자가 아닙니다: $name")

private fun JsonObject.requiredString(name: String): String = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: error("운동 번역 문자열이 비어 있습니다: $name")

private fun JsonObject.requiredStringList(name: String): List<String> {
    val array = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: error("운동 번역 필드가 없거나 배열이 아닙니다: $name")
    require(array.size() > 0) { "운동 번역 배열이 비어 있습니다: $name" }
    return array.mapIndexed { index, element ->
        element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("운동 번역 문자열이 비어 있습니다: $name[$index]")
    }
}

private fun JsonObject.requiredStringMap(name: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    requiredObject(name).entrySet().forEach { (key, value) ->
        val normalizedKey = normalizeKey(key)
        require(normalizedKey.isNotEmpty()) { "운동 번역 키가 비어 있습니다: $name" }
        val translated = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
        require(!translated.isNullOrEmpty()) { "운동 번역 값이 비어 있습니다: $name.$key" }
        require(result.put(normalizedKey, translated) == null) { "정규화 후 중복된 운동 번역 키입니다: $name.$key" }
    }
    return result
}

private fun normalizeKey(value: String): String = value.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

private fun fingerprint(exercise: Exercise): String {
    val normalizedName = exercise.name.trim().replace(WHITESPACE, " ")
    val normalizedInstructions = exercise.instructions.joinToString("\n") { it.trim().replace(WHITESPACE, " ") }
    val normalized = "$normalizedName\n$normalizedInstructions"
    val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
