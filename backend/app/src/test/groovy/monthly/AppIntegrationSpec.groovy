package monthly

import groovy.json.JsonSlurper
import monthly.db.Database
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class AppIntegrationSpec extends Specification {

    @Shared App app
    @Shared int port
    @Shared HttpClient client = HttpClient.newHttpClient()

    def setupSpec() {
        app = new App(Database.inMemory(), 0).start()   // port 0 = OS picks a free port
        port = app.port()
        // seed the shared in-memory DB so the query tests have data regardless of order
        post("/api/imports/revolut", fixtureBytes("revolut_fixture.csv"))
    }

    def cleanupSpec() {
        app?.stop()
    }

    def "the import endpoint accepts a raw Revolut export"() {
        when: "the same month is imported again (replace-strategy makes this idempotent)"
        def resp = post("/api/imports/revolut", fixtureBytes("revolut_fixture.csv"))

        then:
        resp.statusCode() == 200
        new JsonSlurper().parseText(resp.body()).bank == "REVOLUT"
    }

    def "the month summary reflects the four imported rows"() {
        when:
        def resp = get("/api/months/2026-06/summary")
        def summary = new JsonSlurper().parseText(resp.body())

        then:
        resp.statusCode() == 200
        summary.income   == 150.00   // 50.00 + 100.00
        summary.expenses == -29.00    // -25.50 + -3.50
        summary.net      == 121.00
    }

    def "the month endpoint returns all categorized transactions"() {
        when:
        def resp = get("/api/months/2026-06")
        def rows = new JsonSlurper().parseText(resp.body())

        then:
        resp.statusCode() == 200
        rows.size() == 4
    }

    def "a malformed month is rejected with 400"() {
        expect:
        get("/api/months/not-a-month/summary").statusCode() == 400
    }

    def "budgets can be set, listed, and cleared"() {
        when:
        def setResp = put("/api/budgets/groceries", '{"amount":400.00}')

        then:
        setResp.statusCode() == 200
        new JsonSlurper().parseText(get("/api/budgets").body()).GROCERIES == 400.00

        when:
        delete("/api/budgets/groceries")

        then:
        new JsonSlurper().parseText(get("/api/budgets").body()).GROCERIES == null
    }

    def "the budget report returns a line for a configured category"() {
        given:
        put("/api/budgets/groceries", '{"amount":500.00}')

        when:
        def resp = get("/api/budgets/report?month=2026-06")
        def report = new JsonSlurper().parseText(resp.body())

        then:
        resp.statusCode() == 200
        report.month == "2026-06"
        with(report.lines.find { it.category == "GROCERIES" }) {
            it.limit == 500.00
            it.overBudget == false
        }

        cleanup:
        delete("/api/budgets/groceries")
    }

    def "a non-positive budget amount is rejected with 400"() {
        expect:
        put("/api/budgets/groceries", '{"amount":-5}').statusCode() == 400
    }

    def "the CSV export endpoint returns a downloadable file for the month"() {
        when:
        def resp = get("/api/months/2026-06/export.csv")

        then:
        resp.statusCode() == 200
        resp.headers().firstValue("Content-Type").get().startsWith("text/csv")
        resp.headers().firstValue("Content-Disposition").get().contains("attachment")
        resp.body().startsWith("Date,Description,Category,Source,Amount,Currency,Transfer,Manual")
        resp.body().contains("Income,")
    }

    def "the recurring endpoint returns a JSON array"() {
        when:
        def resp = get("/api/recurring")

        then:
        resp.statusCode() == 200
        resp.headers().firstValue("Content-Type").get().startsWith("application/json")
        new JsonSlurper().parseText(resp.body()) instanceof List
    }

    def "a recurring name can be saved"() {
        expect:
        put("/api/recurring/name", '{"key":"REVOLUT|netflix|-10","name":"Netflix"}').statusCode() == 200
    }

    def "saving a recurring name requires a key"() {
        expect:
        put("/api/recurring/name", '{"name":"Netflix"}').statusCode() == 400
    }

    def "a recurring series can be dismissed"() {
        expect:
        deleteWithBody("/api/recurring", '{"key":"REVOLUT|netflix|-10"}').statusCode() == 200
    }

    def "dismissing a series requires a key"() {
        expect:
        deleteWithBody("/api/recurring", '{}').statusCode() == 400
    }

    def "a dismissed series can be restored"() {
        expect:
        postJson("/api/recurring/restore", '{"key":"REVOLUT|netflix|-10"}').statusCode() == 200
    }

    def "the categorization suggestions endpoint returns a JSON array"() {
        when:
        def resp = get("/api/categorization/suggestions")

        then:
        resp.statusCode() == 200
        resp.headers().firstValue("Content-Type").get().startsWith("application/json")
        new JsonSlurper().parseText(resp.body()) instanceof List
    }


    // HELPERS

    private HttpResponse<String> get(String path) {
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}")).GET().build(),
                HttpResponse.BodyHandlers.ofString())
    }

    private HttpResponse<String> post(String path, byte[] body) {
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}"))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString())
    }

    private byte[] fixtureBytes(String name) {
        def stream = getClass().getResourceAsStream("/fixtures/${name}")
        assert stream != null : "fixture ${name} not found on classpath"
        stream.bytes
    }

    private HttpResponse<String> put(String path, String jsonBody) {
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString())
    }

    private HttpResponse<String> delete(String path) {
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}"))
                .DELETE().build(),
                HttpResponse.BodyHandlers.ofString())
    }
    private HttpResponse<String> deleteWithBody(String path, String jsonBody) {
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}"))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString())
    }

    private HttpResponse<String> postJson(String path, String jsonBody) {
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString())
    }
}