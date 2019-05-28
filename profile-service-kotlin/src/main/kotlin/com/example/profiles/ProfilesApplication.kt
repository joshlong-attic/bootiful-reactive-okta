package com.example.profiles

import com.fasterxml.jackson.databind.ObjectMapper
import org.reactivestreams.Publisher
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Consumer

@SpringBootApplication
class ProfilesApplication {

	@Bean
	fun executor() = Executors.newSingleThreadExecutor()

}

fun main(args: Array<String>) {
	runApplication<ProfilesApplication>(*args)
}


@RestController
@RequestMapping(
		value = ["/profiles"],
		produces = [MediaType.APPLICATION_JSON_VALUE]
)
class ProfileRestController(
		val repository: ProfileRepository,
		val service: ProfileService) {

	private val mediaType = MediaType.APPLICATION_JSON

	@DeleteMapping
	fun deleteAll() = this.repository.deleteAll()

	@GetMapping("/{id}")
	fun getById(@PathVariable id: String) = this.repository.findById(id)

	@GetMapping
	fun all() = this.repository.findAll()

	@PostMapping
	fun create(@RequestBody profile: Profile): Publisher<ResponseEntity<Profile>> =
			this.service
					.create(profile.email!!)
					.flatMap { repository.findById(it.id!!) }
					.map {
						ResponseEntity
								.created(URI.create("/profiles/${it.id}"))
								.contentType(this.mediaType)
								.body(it)
					}
}

@Component
class ProfileCreatedEventPublisher(val executor: Executor) :
		ApplicationListener<ProfileCreatedEvent>,
		Consumer<FluxSink<ProfileCreatedEvent>> {

	private val queue = LinkedBlockingQueue<ProfileCreatedEvent>()

	override fun onApplicationEvent(p0: ProfileCreatedEvent) {
		this.queue.offer(p0)
	}

	override fun accept(sink: FluxSink<ProfileCreatedEvent>) {
		this.executor.execute {
			while (true) {
				sink.next(queue.take())
			}
		}
	}
}


@Service
class ProfileService(
		val repository: ProfileRepository,
		val publisher: ApplicationEventPublisher) {

	fun create(email: String) = this.repository
			.save(Profile(email = email))
			.doOnSuccess { p -> this.publisher.publishEvent(ProfileCreatedEvent(p)) }
}

interface ProfileRepository : ReactiveCrudRepository<Profile, String>

data class Profile(var id: String? = null, var email: String? = null)

data class ProfileCreatedEvent(val profile: Profile) : ApplicationEvent(profile)

@Configuration
class WebsocketConfiguration(val om: ObjectMapper) {

	@Bean
	fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()

	@Bean
	fun webSocketHandler(pcep: ProfileCreatedEventPublisher): WebSocketHandler {
		val events = Flux.create(pcep).share()
		return WebSocketHandler { session ->
			val messages = events
					.map { om.writeValueAsString(it) }
					.map { session.textMessage(it) }
			session.send(messages)
		}
	}

	@Bean
	fun simpleUrlHandlerMapping(wsh: WebSocketHandler) = object : SimpleUrlHandlerMapping() {
		init {
			order = 10
			urlMap = mapOf("/ws/profiles" to wsh)
		}
	}
}