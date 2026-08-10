package com.cdcwallet.extraction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadGateTest {

    @Test
    fun `closed by default`() {
        assertFalse(LoadGate().isOpen)
    }

    @Test
    fun `opens on first page start and stays open`() {
        val gate = LoadGate()
        gate.open()
        gate.open()
        assertTrue(gate.isOpen)
    }

    @Test
    fun `gates are independent per load`() {
        val previous = LoadGate()
        val current = LoadGate()
        previous.open()
        assertTrue(previous.isOpen)
        assertFalse("a previous load's open must not admit the next load", current.isOpen)
    }
}
