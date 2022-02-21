package app.plugins

import app.config.App
import app.config.Shopify
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.parametersOf
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.url

fun Application.configureRouting() {

    val client = HttpClient {
        install(JsonFeature)
    }

    routing {
        get("/auth/start") {
            val shop = call.request.queryParameters["shop"]

            if (shop == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {

                val scopes = listOf(
                    "read_customers",
                    "write_customers",
                    "read_draft_orders",
                    "write_draft_orders",
                )

                val redirectUrl = url {
                    protocol = URLProtocol.HTTPS
                    host = shop
                    path("admin", "oauth", "authorize")
                    parameters
                        .appendAll(
                            parametersOf(
                                "client_id" to listOf(Shopify.appAPIKey),
                                "scope" to listOf(scopes.joinToString(",")),
                                "redirect_uri" to listOf("${App.baseUrl}/auth/finished"),
                            )
                        )
                }

                call.respondRedirect(redirectUrl)
            }
        }

        get("/auth/finished") {
            val code = call.request.queryParameters["code"]
            val shop = call.request.queryParameters["shop"]

            if (code == null || shop == null) {
                throw IllegalStateException(
                    "Request parameter has illegal state. code=$code, shop=$shop. Request=${call.request}"
                )
            } else {
                val response = client.post<HashMap<String, String>>(
                    "https://$shop/admin/oauth/access_token"
                ) {
                    contentType(ContentType.Application.Json)
                    body = mapOf(
                        "code" to code,
                        "client_id" to Shopify.appAPIKey,
                        "client_secret" to Shopify.appAPISecretKey,
                    )
                }

                println(response)

                call.respondRedirect("https://$shop/admin/apps")
            }
        }
    }
}
