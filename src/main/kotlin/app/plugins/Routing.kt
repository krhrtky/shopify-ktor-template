package app.plugins

import app.config.App
import app.config.Shopify
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.parametersOf
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.toMap
import io.ktor.util.url
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

fun Application.configureRouting() {

    val client = HttpClient {
        install(JsonFeature)
    }

    routing {
        intercept(ApplicationCallPipeline.Monitoring) {

            if (!call.request.path().startsWith("/auth")) {
                return@intercept
            }

            val hmac = call.request.queryParameters["hmac"]

            val withoutHmac = call
                .request
                .queryParameters
                .toMap()
                .filterNot { it.key == "hmac" }
                .toSortedMap()
                .map { "${it.key}=${it.value.joinToString("")}" }
                .joinToString("&")

            println("withoutHmac($withoutHmac)")

            val mac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(
                Shopify.appAPISecretKey.toByteArray(),
                "HmacSHA256",
            )
            mac.init(keySpec)

            val signBytes = mac.doFinal(withoutHmac.toByteArray())
            val builder = StringBuilder()
            for (signByte in signBytes) {
                builder.append(String.format("%02x", signByte and 0xff.toByte()))
            }

            if (builder.toString() != hmac) {
                call.respond(HttpStatusCode.BadRequest)
            }
        }

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

            val shop = call.request.queryParameters["shop"]
            val code = call.request.queryParameters["code"]

            if (code == null || shop == null) {
                throw IllegalStateException(
                    "Request parameter has illegal state. code=$code, shop=$shop. Request=${call.request}"
                )
            } else {
                val response = client.post<HttpResponse>(
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
