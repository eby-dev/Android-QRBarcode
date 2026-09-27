package com.ahmadabuhasan.qrbarcode.ui.batch

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

// Robolectric provides the main looper that LiveData.setValue needs.
@RunWith(RobolectricTestRunner::class)
class BatchScanViewModelTest {

    private val gap = BatchScanViewModel.REPEAT_GAP_MS

    @Test
    fun `new code is added once while it stays in view`() {
        val vm = BatchScanViewModel(SavedStateHandle())
        assertTrue(vm.onDetected("A", "EAN_13", now = 0))
        // Frames keep reporting the code while it is held in front of the camera.
        assertFalse(vm.onDetected("A", "EAN_13", now = 100))
        assertFalse(vm.onDetected("A", "EAN_13", now = gap + 50))
        assertEquals(1, vm.list.value!!.single().count)
    }

    @Test
    fun `code counts again after leaving view`() {
        val vm = BatchScanViewModel(SavedStateHandle())
        vm.onDetected("A", "EAN_13", now = 0)
        assertTrue(vm.onDetected("A", "EAN_13", now = gap))
        assertEquals(2, vm.list.value!!.single().count)
    }

    @Test
    fun `same content with a different format is a separate item`() {
        val vm = BatchScanViewModel(SavedStateHandle())
        vm.onDetected("123", "EAN_13", now = 0)
        vm.onDetected("123", "CODE_128", now = 10)
        assertEquals(2, vm.list.value!!.size)
    }

    @Test
    fun `newest scan is listed first, and a rescan moves to the top`() {
        val vm = BatchScanViewModel(SavedStateHandle())
        vm.onDetected("A", "QR_CODE", now = 0)
        vm.onDetected("B", "QR_CODE", now = 10)
        assertEquals(listOf("B", "A"), vm.list.value!!.map { it.content })
        vm.onDetected("A", "QR_CODE", now = gap + 10)
        assertEquals(listOf("A", "B"), vm.list.value!!.map { it.content })
    }

    @Test
    fun `removed code can be added again immediately`() {
        val vm = BatchScanViewModel(SavedStateHandle())
        vm.onDetected("A", "QR_CODE", now = 0)
        vm.remove(vm.list.value!!.single())
        assertTrue(vm.list.value!!.isEmpty())
        assertTrue(vm.onDetected("A", "QR_CODE", now = 10))
    }

    @Test
    fun `list survives process death through SavedStateHandle`() {
        val state = SavedStateHandle()
        BatchScanViewModel(state).apply {
            onDetected("A", "QR_CODE", now = 0)
            onDetected("A", "QR_CODE", now = gap)
            onDetected("B", "EAN_13", now = gap + 1)
        }
        val restored = BatchScanViewModel(state).list.value!!
        assertEquals(listOf("B", "A"), restored.map { it.content })
        assertEquals(2, restored.first { it.content == "A" }.count)
    }

    @Test
    fun `text export shows counts above one`() {
        val vm = BatchScanViewModel(SavedStateHandle())
        vm.onDetected("A", "QR_CODE", now = 0)
        vm.onDetected("A", "QR_CODE", now = gap)
        vm.onDetected("B", "QR_CODE", now = gap + 1)
        assertEquals("A ×2\nB", vm.toText())
    }

    // --- CSV ---

    @Test
    fun `csv has bom, header and one row per item in scan order`() {
        val csv = BatchCsv.build(
            listOf(BatchItem("8991002101234", "EAN_13", 3, 0L)),
            TimeZone.getTimeZone("UTC"),
        )
        assertEquals(
            "﻿content,format,count,last_scanned\r\n8991002101234,EAN_13,3,1970-01-01 00:00:00\r\n",
            csv,
        )
    }

    @Test
    fun `csv quotes commas, quotes and newlines`() {
        assertEquals("\"a,b\"", BatchCsv.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", BatchCsv.cell("say \"hi\""))
        assertEquals("\"line1\nline2\"", BatchCsv.cell("line1\nline2"))
        assertEquals("plain", BatchCsv.cell("plain"))
    }

    @Test
    fun `csv neutralises values a spreadsheet would run as formulas`() {
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", BatchCsv.cell("=HYPERLINK(\"x\")"))
        assertEquals("'+1", BatchCsv.cell("+1"))
        assertEquals("'@cmd", BatchCsv.cell("@cmd"))
        assertEquals("'-2", BatchCsv.cell("-2"))
    }
}
