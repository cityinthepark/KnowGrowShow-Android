package com.stoneofhope.stoneofhope.ui.bible

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleBook
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.BibleRefConfig
import com.stoneofhope.stoneofhope.data.bible.BibleRefLevel
import com.stoneofhope.stoneofhope.data.bible.BibleRefProblem
import com.stoneofhope.stoneofhope.data.bible.BibleRefValidation
import com.stoneofhope.stoneofhope.data.bible.BibleReferenceValidator
import kotlinx.coroutines.delay

/**
 * Book-name languages for one screen, from the selected Bible versions (iOS `BibleVersionHelper`).
 * [matching] = `bookMatchingLanguages()`, [display] / [secondary] = the book picker's two columns.
 */
data class BibleRefLanguages(
    val matching: List<BibleLanguage>,
    val display: BibleLanguage,
    val secondary: BibleLanguage?,
)

/** Which help text the info button shows. */
enum class BibleRefHelp { Note, Prayer, Sermon, Chapter }

private enum class DisplayState { Valid, Invalid, Quiet }

private val ValidGreen = Color(0xFF34C759)
private val InvalidRed = Color(0xFFFF3B30)

/**
 * iOS `BibleReferenceField` with the `BookPicker` chip on the left and a help button on the right.
 * Only two visible states: green check when the reference is complete for [config], red when it can
 * no longer become valid. "Legal but unfinished" stays quiet while typing and hints on blur; unknown
 * books and reversed ranges wait for a 400 ms pause (an IME may hold raw pinyin, "5:3-1" may become
 * "5:3-12").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleReferenceField(
    value: String,
    onValueChange: (String) -> Unit,
    config: BibleRefConfig,
    languages: BibleRefLanguages,
    help: BibleRefHelp,
    showAbbreviations: Boolean,
    modifier: Modifier = Modifier,
    onValidationChange: (BibleRefValidation) -> Unit = {},
) {
    val validation = remember(value, config, languages) { BibleReferenceValidator.validate(value, config, languages.matching) }
    var focused by remember { mutableStateOf(false) }
    var typingPaused by remember { mutableStateOf(true) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var selectedOrd by rememberSaveable { mutableStateOf<Int?>(null) }
    var focusAfterPick by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(value) {
        typingPaused = false
        delay(400)
        typingPaused = true
    }
    LaunchedEffect(validation) { onValidationChange(validation) }
    LaunchedEffect(focusAfterPick) {
        if (focusAfterPick > 0) {
            delay(250) // let the book list finish closing before raising the keyboard
            focusRequester.requestFocus()
        }
    }

    val display = when (validation) {
        is BibleRefValidation.Valid -> DisplayState.Valid
        is BibleRefValidation.Invalid ->
            if (validation.problem.deservesTypingGrace && !typingPaused && focused) DisplayState.Quiet else DisplayState.Invalid
        else -> DisplayState.Quiet
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = { pickerOpen = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(56.dp).testTag("bookPicker"),
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✝️")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.bible_ref_book), tint = MaterialTheme.colorScheme.primary)
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.bible_ref_placeholder)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next,
                ),
                trailingIcon = when (display) {
                    DisplayState.Valid -> { { Icon(Icons.Filled.CheckCircle, stringResource(R.string.bible_ref_valid), tint = ValidGreen) } }
                    DisplayState.Invalid -> { { Icon(Icons.Filled.Error, stringResource(R.string.bible_ref_invalid), tint = InvalidRed) } }
                    DisplayState.Quiet -> null
                },
                shape = RoundedCornerShape(14.dp),
                colors = when (display) {
                    DisplayState.Valid -> OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ValidGreen.copy(alpha = 0.55f), unfocusedBorderColor = ValidGreen.copy(alpha = 0.55f),
                    )
                    DisplayState.Invalid -> OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = InvalidRed.copy(alpha = 0.55f), unfocusedBorderColor = InvalidRed.copy(alpha = 0.55f),
                    )
                    DisplayState.Quiet -> OutlinedTextFieldDefaults.colors()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .testTag("bibleRefField"),
            )
            IconButton(onClick = { helpOpen = true }) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.bible_ref_help), tint = MaterialTheme.colorScheme.primary)
            }
        }
        StatusLine(validation, display, focused, value, languages.display)
    }

    if (pickerOpen) {
        BookPickerSheet(
            selectedOrd = selectedOrd,
            languages = languages,
            showAbbreviations = showAbbreviations,
            onDismiss = { pickerOpen = false },
            onSelect = { book ->
                selectedOrd = book.ord
                val title = book.fullName(languages.display)
                // iOS BookPicker: only replace the text when it doesn't already name this book.
                if (!value.contains(title)) onValueChange("$title ")
                pickerOpen = false
                focusAfterPick++
            },
        )
    }
    if (helpOpen) ReferenceHelpDialog(help) { helpOpen = false }
}

@Composable
private fun StatusLine(validation: BibleRefValidation, display: DisplayState, focused: Boolean, value: String, language: BibleLanguage) {
    val style = MaterialTheme.typography.bodySmall
    when (validation) {
        is BibleRefValidation.Valid -> {
            // Echo the normalised reference only when it says more than the field ("Jn 3:16" → "John 3:16").
            val echo = validation.ref.displayText(language)
            if (squashed(echo) != squashed(value)) {
                Text(echo, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("bibleRefEcho"))
            }
        }
        is BibleRefValidation.Invalid -> if (display == DisplayState.Invalid) {
            Text(problemMessage(validation.problem), style = style, color = InvalidRed, modifier = Modifier.testTag("bibleRefError"))
        }
        is BibleRefValidation.Incomplete -> if (!focused) {
            val hint = if (validation.missing == BibleRefLevel.Chapter) R.string.bible_ref_needs_chapter else R.string.bible_ref_needs_verse
            Text(stringResource(hint), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BibleRefValidation.Empty -> Unit
    }
}

private fun squashed(s: String) = s.lowercase().filterNot { it.isWhitespace() }

/** iOS `BibleRefProblem.message`; book names in the device language like iOS `getName(for:)`. */
@Composable
private fun problemMessage(problem: BibleRefProblem): String = when (problem) {
    BibleRefProblem.UnknownBook -> stringResource(R.string.bible_ref_unknown_book)
    is BibleRefProblem.ChapterOutOfRange ->
        pluralStringResource(R.plurals.bible_ref_chapter_out_of_range, problem.max, BibleBooks.name(problem.bookOrd), problem.max)
    is BibleRefProblem.VerseOutOfRange ->
        pluralStringResource(R.plurals.bible_ref_verse_out_of_range, problem.max, BibleBooks.name(problem.bookOrd), problem.chapter, problem.max)
    BibleRefProblem.ReversedVerseRange -> stringResource(R.string.bible_ref_reversed_range)
    BibleRefProblem.VerseRangeNotAllowed -> stringResource(R.string.bible_ref_range_not_allowed)
    BibleRefProblem.InvalidChapterList -> stringResource(R.string.bible_ref_invalid_chapter_list)
}

/** iOS `BibleBookHelper.bookDisplayLabel`: "Exodus (Exo, Ex)". */
private fun bookLabel(book: BibleBook, language: BibleLanguage, withAbbreviations: Boolean): String {
    val title = book.fullName(language)
    if (!withAbbreviations) return title
    val abbrs = book.abbreviations(language).take(2)
    return if (abbrs.isEmpty()) "$title (?)" else "$title (${abbrs.joinToString(", ")})"
}

/** iOS `BookSelectionSheet`: 66 books, primary language label, secondary language on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookPickerSheet(
    selectedOrd: Int?,
    languages: BibleRefLanguages,
    showAbbreviations: Boolean,
    onDismiss: () -> Unit,
    onSelect: (BibleBook) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ((selectedOrd ?: 1) - 1).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.bible_ref_book), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.bible_ref_close)) }
        }
        LazyColumn(state = listState, modifier = Modifier.testTag("bookList")) {
            items(BibleBooks.all, key = { it.ord }) { book ->
                ListItem(
                    headlineContent = { Text(bookLabel(book, languages.display, showAbbreviations)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            languages.secondary?.let {
                                Text(
                                    bookLabel(book, it, showAbbreviations),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (book.ord == selectedOrd) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    modifier = Modifier.clickable { onSelect(book) }.testTag("book_${book.ord}"),
                )
            }
        }
    }
}

/**
 * Verse-level examples. iOS shows the *sermon* help here ("3, 5:1–3", "6"), which these
 * verse-only fields reject (see docs/IOS_ISSUES.md).
 */
@Composable
private fun ReferenceHelpDialog(help: BibleRefHelp, onDismiss: () -> Unit) {
    val examples = when (help) {
        // iOS `Sermon Ref Example 1–4`, with a book name (Android's picker fills the book in the field).
        BibleRefHelp.Sermon -> listOf(
            "Romans 2:1-5" to R.string.bible_ref_help_sermon_verses,
            "Romans 3, 5:1-3" to R.string.bible_ref_help_sermon_chapters,
            "Romans 6" to R.string.bible_ref_help_sermon_chapter,
            "罗 8 · 羅馬書 8" to R.string.bible_ref_help_example_chinese,
        )
        else -> listOf(
            "John 3:16" to R.string.bible_ref_help_example_verse,
            "Jn 3:16-18" to R.string.bible_ref_help_example_range,
            "约 3:16 · 約翰福音 3：16" to R.string.bible_ref_help_example_chinese,
            "Jude 5" to R.string.bible_ref_help_example_one_chapter,
        )
    }
    val intro = when (help) {
        BibleRefHelp.Sermon -> R.string.bible_ref_help_sermon_intro
        BibleRefHelp.Chapter -> R.string.bible_ref_help_chapter_intro
        else -> R.string.bible_ref_help_intro
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bible_ref_help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(intro))
                examples.forEach { (input, desc) ->
                    val description = stringResource(desc)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("• $input") }
                            append("  → $description")
                        },
                    )
                }
                if (help == BibleRefHelp.Note || help == BibleRefHelp.Prayer) {
                    Text(stringResource(R.string.bible_ref_help_chapter_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (help == BibleRefHelp.Note) {
                    Text(stringResource(R.string.bible_ref_help_note_shortcut), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.bible_ref_done)) } },
    )
}
