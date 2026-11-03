package com.abcamiletto.wortschatz.domain

import com.abcamiletto.wortschatz.ui.fold
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
}
