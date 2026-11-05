package com.abcamiletto.wortschatz.domain

import com.abcamiletto.wortschatz.ui.fold
import com.abcamiletto.wortschatz.ui.keepWithItsWord
import com.abcamiletto.wortschatz.ui.marked
import com.abcamiletto.wortschatz.ui.withThousands
import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test fun foldIgnoresCaseAndUmlauts() = assertEquals("muller strasse cafe", fold("Müller Straße Café"))

    @Test fun thousandsAreSeparated() {
        assertEquals("999", 999.withThousands())
        assertEquals("3,085", 3085.withThousands())
        assertEquals("1,234,567", 1234567.withThousands())
    }

    @Test fun aPromptOnlyWrapsBetweenWordsAndTheirMeanings() {
        assertEquals("the\u00A0recording, the\u00A0photo", keepWithItsWord("the recording, the photo"))
        assertEquals("to\u00A0move house, to\u00A0go", keepWithItsWord("to move house, to go"))
        assertEquals("photo album", keepWithItsWord("photo album"))
    }

    @Test fun markedTextLosesItsAsterisksAndKeepsTheBoldParts() {
        val text = marked("Ich **rufe** dich **an**.")
        assertEquals("Ich rufe dich an.", text.text)
        assertEquals(listOf("rufe", "an"), text.spanStyles.map { text.text.substring(it.start, it.end) })
    }
}
