package dev.russell.fatebook.data.remote

import dev.russell.fatebook.data.preferences.UserPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyInterceptor @Inject constructor(
    private val userPreferences: UserPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val apiKey = userPreferences.apiKey ?: return chain.proceed(original)

        val url = original.url.newBuilder()
            .addQueryParameter("apiKey", apiKey)
            .build()

        val request = original.newBuilder()
            .url(url)
            .build()

        return chain.proceed(request)
    }
}
