// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader

import android.app.Activity
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryEntry
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryStore
import com.gitlab.mudlej.MjPdfReader.databinding.DictionaryDefinitionSheetBinding
import com.gitlab.mudlej.MjPdfReader.databinding.DictionarySenseRowBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictionaryDefinitionController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val isDefineWordsEnabled: () -> Boolean,
) {

    fun defineWord(word: String, onTranslateInstead: () -> Unit): Boolean {
        if (!isDefineWordsEnabled() || !DictionaryStore.isInstalled(activity)) {
            return false
        }
        scope.launch {
            val entry = withContext(Dispatchers.IO) { DictionaryStore.lookup(activity, word) }
            if (entry == null) {
                onTranslateInstead()
            } else {
                showSheet(entry, onTranslateInstead)
            }
        }
        return true
    }

    private fun showSheet(entry: DictionaryEntry, onTranslateInstead: () -> Unit) {
        val dialog = BottomSheetDialog(activity)
        val sheetBinding = DictionaryDefinitionSheetBinding.inflate(activity.layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.dictionaryWordTitle.text = entry.lemma
        val senseCounts = mutableMapOf<String, Int>()
        entry.senses.forEach { sense ->
            val number = (senseCounts[sense.pos] ?: 0) + 1
            senseCounts[sense.pos] = number
            val row = DictionarySenseRowBinding.inflate(activity.layoutInflater, sheetBinding.sensesContainer, true)
            row.senseLabel.text = "${activity.getString(posLabelRes(sense.pos))} $number"
            row.senseDefinition.text = sense.definition
            sense.example?.let {
                row.senseExample.text = it
                row.senseExample.visibility = View.VISIBLE
            }
            sense.synonyms?.let {
                row.senseSynonyms.text = activity.getString(R.string.dictionary_synonyms, it)
                row.senseSynonyms.visibility = View.VISIBLE
            }
        }
        sheetBinding.translateInsteadButton.setOnClickListener {
            dialog.dismiss()
            onTranslateInstead()
        }
        dialog.show()
    }

    private fun posLabelRes(pos: String): Int = when (pos) {
        "n" -> R.string.dictionary_pos_noun
        "v" -> R.string.dictionary_pos_verb
        "a" -> R.string.dictionary_pos_adjective
        else -> R.string.dictionary_pos_adverb
    }
}
