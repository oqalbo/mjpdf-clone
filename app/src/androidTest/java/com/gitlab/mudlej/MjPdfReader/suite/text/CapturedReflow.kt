// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

object CapturedReflow {

    const val STRUCTURED_PAGE_1_DEFAULT = "Chapter One\n\nThe reading of a document is a quiet negotiation between the page and the eye, and the negotiation is settled long before the reader is aware that it has begun. Each paragraph offers a shape, and the reader either accepts that shape or refuses it and reads the page some other way, which is the reader's right and the typesetter's quiet defeat.\n\nWhere the text is dense the eye slows, and where it opens out the eye runs ahead of the understanding and has to be called back. This is the ordinary rhythm of reading, and a reader that respects it disappears into the background of the work it is doing."
    const val STRUCTURED_PAGE_1_FIRST_PARAGRAPH = "The reading of a document is a quiet negotiation between the page and the eye, and the negotiation is settled long before the reader is aware that it has begun. Each paragraph offers a shape, and the reader either accepts that shape or refuses it and reads the page some other way, which is the reader's right and the typesetter's quiet defeat."
    const val STRUCTURED_PAGE_1_JOIN_OFF = "Chapter One\nThe reading of a document is a quiet negotiation between the page and\nthe eye, and the negotiation is settled long before the reader is aware\nthat it has begun. Each paragraph offers a shape, and the reader either\naccepts that shape or refuses it and reads the page some other way,\nwhich is the reader's right and the typesetter's quiet defeat.\nWhere the text is dense the eye slows, and where it opens out the eye\nruns ahead of the understanding and has to be called back. This is the\nordinary rhythm of reading, and a reader that respects it disappears\ninto the background of the work it is doing."
    const val STRUCTURED_PAGE_3_SWITCHES_OFF = "The binder called the finished volume an extraordinary piece of work, and then charged for it at the ordinary rate,\nwhich the keeper recorded in the ledger without any comment at all."
    const val STRUCTURED_PAGE_4_DEFAULT = "The lens itself was a magnificent thing, ground in three stages by a firm that closed the year after it was fitted, and the keeper wrote in his own hand1 that no one had ever built its equal on that coast.\n\n1 The keeper's hand is steady until the last year."

    const val NORMAL_PAGE_5_PREFIX = "Page 5\n\nThe reading of a document is a q"

    const val PROTECTED_PAGE_1_DEFAULT = "Page 1\n\nThe reading of a document is a quiet negotiation between the page and the eye. Each paragraph offers a shape, and the reader accepts or refuses it. Where the text is dense the eye slows, and where it opens out the eye moves ahead of the understanding. This is the ordinary rhythm of reading, and a reader that respects it disappears into the background. The reading of a document is a quiet negotiation between the page and the eye. Each paragraph offers a shape, and the reader accepts or refuses it. Where the text is dense the eye slows, and where it opens out the eye moves ahead of the understanding. This is the ordinary rhythm of reading, and a reader that respects it disappears into the background. The reading of a document is a quiet negotiation between the page and the eye. Each paragraph offers a shape, and the reader accepts or refuses it. Where the text is dense the eye slows, and where it opens out the eye moves ahead of the understanding. This is the ordinary rhythm of reading, and a reader that respects it disappears into the background."

    const val ARABIC_VOCALISED_WORD = "\u0643\u0650\u062A\u064E\u0627\u0628\u064F"

    const val SELECTION_WORD = "negotiation"
    const val SELECTION_PHRASE = "quiet negotiation between the page and the eye"

    const val PREFIX_LENGTH = 40

    fun compare(name: String, expected: String, actual: CharSequence?) {
        val observed = actual?.toString()
        if (expected.isEmpty()) {
            throw AssertionError(uncapturedMessage(name, observed))
        }
        if (observed != expected) {
            throw AssertionError(
                "$name does not match the captured reflow string.\n" +
                    "expected: ${literal(expected)}\n" +
                    "actual:   ${literal(observed)}"
            )
        }
    }

    fun require(name: String, expected: String): String {
        if (expected.isEmpty()) {
            throw AssertionError(uncapturedMessage(name, null))
        }
        return expected
    }

    fun literal(value: String?): String {
        if (value == null) return "null"
        val escaped = StringBuilder()
        value.forEach { character ->
            when (character) {
                '\\' -> escaped.append("\\\\")
                '"' -> escaped.append("\\\"")
                '\n' -> escaped.append("\\n")
                '\r' -> escaped.append("\\r")
                '\t' -> escaped.append("\\t")
                '$' -> escaped.append("\\$")
                else -> if (character.code in 0x20..0x7E) {
                    escaped.append(character)
                } else {
                    escaped.append(String.format("\\u%04X", character.code))
                }
            }
        }
        return "\"$escaped\""
    }

    private fun uncapturedMessage(name: String, observed: String?): String =
        "CapturedReflow.$name is empty. The value this run produced is:\n${literal(observed)}"
}
