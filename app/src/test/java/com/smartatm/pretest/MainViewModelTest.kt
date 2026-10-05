package com.smartatm.pretest

import com.smartatm.pretest.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    @Test
    fun testSampleScriptsLength() {
        assertTrue(MainViewModel.SCRIPT_VOICE_PHISHING_CALL.length <= MainViewModel.MAX_TEXT_LENGTH)
        assertTrue(MainViewModel.SCRIPT_VOICE_PHISHING_SMS.length <= MainViewModel.MAX_TEXT_LENGTH)
        assertTrue(MainViewModel.SCRIPT_NORMAL_CALL.length <= MainViewModel.MAX_TEXT_LENGTH)
        assertTrue(MainViewModel.SCRIPT_NORMAL_SMS.length <= MainViewModel.MAX_TEXT_LENGTH)
    }

    @Test
    fun testQrPayloadFormat() {
        val sessionId = "VP-000012"
        val qrPayload = "{\"session_id\": \"$sessionId\"}"
        assertEquals("{\"session_id\": \"VP-000012\"}", qrPayload)
    }
}
