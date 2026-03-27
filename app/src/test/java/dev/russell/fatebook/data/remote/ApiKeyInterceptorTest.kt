package dev.russell.fatebook.data.remote

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.preferences.UserPreferences
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class ApiKeyInterceptorTest {

    private val prefs = mockk<UserPreferences>(relaxed = true)
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(prefs))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds apiKey query param when key exists`() {
        every { prefs.apiKey } returns "my-secret-key"
        server.enqueue(MockResponse().setBody("ok"))

        client.newCall(
            Request.Builder().url(server.url("/test")).build()
        ).execute()

        val request = server.takeRequest()
        assertThat(request.requestUrl!!.queryParameter("apiKey")).isEqualTo("my-secret-key")
    }

    @Test
    fun `passes through when key is null`() {
        every { prefs.apiKey } returns null
        server.enqueue(MockResponse().setBody("ok"))

        client.newCall(
            Request.Builder().url(server.url("/test")).build()
        ).execute()

        val request = server.takeRequest()
        assertThat(request.requestUrl!!.queryParameter("apiKey")).isNull()
    }
}
