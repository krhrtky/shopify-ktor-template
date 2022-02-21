package app.config

import java.lang.System.getenv

class App {
    companion object {
        val baseUrl = getenv("BASE_URL") ?: throw IllegalStateException()
    }
}
