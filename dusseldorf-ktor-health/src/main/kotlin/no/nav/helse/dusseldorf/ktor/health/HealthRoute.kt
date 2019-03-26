package no.nav.helse.dusseldorf.ktor.health

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.helse.dusseldorf.ktor.core.Paths
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("no.nav.helse.dusseldorf.ktor.health.HealthRoute")

fun Route.HealthRoute(
        path: String = Paths.DEFAULT_HEALTH_PATH,
        healthChecks : Set<HealthCheck>
) {

    get(path) {
        val results = coroutineScope {
            val futures = mutableListOf<Deferred<Result>>()
            healthChecks.forEach { healthCheck ->
                futures.add(async {
                    try {
                        healthCheck.check()
                    } catch (cause: Throwable) {
                        logger.error("Feil ved eksekvering av helsesjekk.", cause)
                        UnHealthy(name = healthCheck.javaClass.simpleName, result = cause.message ?: "Feil ved eksekvering av helsesjekk.")
                    }
                })

            }
            futures.awaitAll()
        }

        val healthy : MutableList<Map<String, Any?>> = mutableListOf()
        val unhealthy : MutableList<Map<String, Any?>> = mutableListOf()

        results.forEach { result ->
            when (result) {
                is Healthy -> healthy.add(result.result())
                else -> unhealthy.add(result.result())
            }
        }


        call.respond(
                status = if (unhealthy.isEmpty()) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                message = mapOf(
                    Pair("healthy", healthy),
                    Pair("unhealthy", unhealthy)
                )
        )
    }
}