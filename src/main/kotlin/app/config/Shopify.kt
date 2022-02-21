package app.config

class Shopify {
    companion object {
        val appAPIKey = System.getenv("SHOPIFY_APP_API_KEY") ?: throw IllegalStateException()
        val appAPISecretKey = System.getenv("SHOPIFY_APP_API_SECRET_KEY") ?: throw IllegalStateException()
    }
}
