package com.stoneofhope.stoneofhope.data.image

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** The words in a photo (iOS `FileHelper.recognizeText`): lines joined by a newline, "" when there are none. */
interface TextRecognizing {
    suspend fun recognize(bitmap: Bitmap): String
}

/** On-device ML Kit; its Chinese recognizer reads Latin text too, like iOS asking Vision for zh-Hant, zh-Hans, en. */
@Singleton
class MlKitTextRecognizer @Inject constructor() : TextRecognizing {
    private val recognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    override suspend fun recognize(bitmap: Bitmap): String = try {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().textBlocks
            .flatMap { it.lines }.joinToString("\n") { it.text }
    } catch (_: Exception) {
        ""
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {
    @Binds abstract fun textRecognizing(impl: MlKitTextRecognizer): TextRecognizing
}
