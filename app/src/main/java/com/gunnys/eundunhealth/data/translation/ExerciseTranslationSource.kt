package com.gunnys.eundunhealth.data.translation

import android.content.Context
import com.gunnys.eundunhealth.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TRANSLATION_ASSET = "korean_exercises.json"

@Singleton
class ExerciseTranslationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    @Suppress("RedundantSuspendModifier") // withContext is suspend; detekt 1.23.x does not resolve this call here.
    suspend fun load(): String = withContext(ioDispatcher) {
        context.assets.open(TRANSLATION_ASSET).bufferedReader().use { it.readText() }
    }
}
