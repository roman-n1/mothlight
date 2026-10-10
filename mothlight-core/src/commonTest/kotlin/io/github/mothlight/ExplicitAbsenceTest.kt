package io.github.mothlight

import kotlin.test.*

class ExplicitAbsenceTest {
    @Test fun bodyReasonsDoNotMasqueradeAsCapturedText() {
        val redactor = Redactor(captureJsonBodies = true, bodyLimit = 64)
        assertEquals(CapturedBody.NotCaptured, redactor.body(CapturedBody.NotCaptured))
        assertEquals(CapturedBody.NotJson, redactor.body(""))
        assertEquals(CapturedBody.NotJson, redactor.body("null"))
        assertEquals(CapturedBody.TooLarge, redactor.body("x".repeat(65)))
        assertEquals(CapturedBody.Text("{\"value\":null}"), redactor.body("{\"value\":null}"))
        assertEquals(CapturedBody.NotCaptured, Redactor().body(CapturedBody.Text("{}")))
    }

    @Test fun requestStatesPreserveZeroDurationAndAnAlreadyCapturedResponse() {
        val inspector = NetworkInspector(redactor = Redactor(captureJsonBodies = true))
        inspector.setEnabled(true)
        val id = inspector.recordStart("GET", "/item", emptyMap(), CapturedBody.NotCaptured, 40)
        assertEquals(Status.InProgress, inspector.transactions.value.single().status)
        inspector.recordSuccess(id, 204, emptyMap(), "{}", 40)
        inspector.recordSuccess(id, 204, emptyMap(), CapturedBody.NotCaptured, 40)
        val transaction = inspector.transactions.value.single()
        assertEquals(Status.Completed(204, 0), transaction.status)
        assertEquals(CapturedBody.Text("{}"), transaction.responseBody)
        inspector.recordFailure(id, "TransportError", 55)
        assertEquals(Status.Failed("TransportError", 15), inspector.transactions.value.single().status)
    }

    @Test fun omittedBodiesAreNeverExportedAsRequestPayloads() {
        fun curl(body: CapturedBody) = buildCurlCommand(NetworkTransaction(
            "id", 1, "POST", "/items", emptyMap(), body, emptyMap(), CapturedBody.NotCaptured, 0, Status.InProgress,
        ))
        for (body in listOf(CapturedBody.NotCaptured, CapturedBody.TooLarge, CapturedBody.NotJson)) {
            assertFalse(curl(body).contains("-d"))
        }
        assertTrue(curl(CapturedBody.Text("{}")).contains("-d '{}'"))
    }
}
