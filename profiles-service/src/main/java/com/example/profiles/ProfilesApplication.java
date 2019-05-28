package com.example.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@RequiredArgsConstructor
@SpringBootApplication
public class ProfilesApplication {

	@Bean
	Executor executor() {
		return Executors.newSingleThreadExecutor();
	}

	public static void main(String[] args) {
		SpringApplication.run(ProfilesApplication.class, args);
	}
}

@Log4j2
@Component
@RequiredArgsConstructor
class ProfileCreatedEventPublisher implements
	ApplicationListener<ProfileCreatedEvent>,
	Consumer<FluxSink<ProfileCreatedEvent>> {

	private final Executor executor;
	private final LinkedBlockingQueue<ProfileCreatedEvent> queue = new LinkedBlockingQueue<>();

	@SneakyThrows
	private ProfileCreatedEvent next() {
		return queue.take();
	}

	@Override
	public void accept(FluxSink<ProfileCreatedEvent> sink) {
		this.executor.execute(() -> {
			while (true)
				sink.next(next());
		});
	}

	@Override
	public void onApplicationEvent(ProfileCreatedEvent profileCreatedEvent) {
		log.info("new " + profileCreatedEvent.getClass().getName() + ":" + profileCreatedEvent.toString());
		this.queue.offer(profileCreatedEvent);
	}
}

@Configuration
@RequiredArgsConstructor
class WebsocketConfiguration {

	private final ObjectMapper objectMapper;

	@Bean
	SimpleUrlHandlerMapping simpleUrlHandlerMapping(WebSocketHandler wsh) {
		return new SimpleUrlHandlerMapping() {
			{
				setOrder(10);
				setUrlMap(Map.of("/ws/profiles", wsh));
			}
		};
	}

	@SneakyThrows
	String from(ProfileCreatedEvent pce) {
		return this.objectMapper.writeValueAsString(pce);
	}

	@Bean
	WebSocketHandler websockets(ProfileCreatedEventPublisher pcep) {
		var events = Flux.create(pcep).share();
		return session -> {
			var messages = events
				.map(this::from)
				.map(session::textMessage);
			return session.send(messages);
		};
	}

	@Bean
	WebSocketHandlerAdapter webSocketHandlerAdapter() {
		return new WebSocketHandlerAdapter();
	}
}

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
class ProfileRestController {

	private final MediaType mediaType = MediaType.APPLICATION_JSON;
	private final ProfileRepository repository;
	private final ProfileService service;

	@DeleteMapping
	Publisher<Void> deleteAll() {
		return this.repository.deleteAll();
	}

	@GetMapping("/{id}")
	Publisher<Profile> getByid(@PathVariable String id) {
		return this.repository.findById(id);
	}

	@GetMapping
	Publisher<Profile> all() {
		return this.repository.findAll();
	}

	@PutMapping("/{id}")
	Publisher<ResponseEntity<Profile>> updateById(@PathVariable String id, @RequestBody Profile profile) {
		return this.repository
			.findById(id)
			.map(p -> new Profile(p.getId(), profile.getEmail()))
			.flatMap(this.repository::save)
			.flatMap(saved -> this.repository.findById(saved.getId()))
			.map(p -> ResponseEntity.ok().body(p));
	}

	@DeleteMapping("/{id}")
	Publisher<Void> deleteById(@PathVariable String id) {
		return this.repository.deleteById(id);
	}

	@PostMapping
	Publisher<ResponseEntity<Profile>> create(@RequestBody Profile profile) {
		return this.service
			.create(profile.getEmail())
			.flatMap(saved -> this.repository.findById(saved.getId()))
			.map(p -> ResponseEntity
				.created(URI.create("/profiles/" + p.getId()))
				.contentType(this.mediaType)
				.body(p)
			);
	}
}

@Service
@RequiredArgsConstructor
class ProfileService {

	private final ProfileRepository repository;
	private final ApplicationEventPublisher publisher;

	Mono<Profile> create(String email) {
		return this.repository
			.save(new Profile(null, email))
			.doOnSuccess(p -> this.publisher.publishEvent(new ProfileCreatedEvent(p.getSource())));
	}
}

class ProfileCreatedEvent extends ApplicationEvent {

	public ProfileCreatedEvent(Profile source) {
		super(source);
	}

	Profile getProfile() {
		return Profile.class.cast(this.getSource());
	}
}

interface ProfileRepository extends ReactiveCrudRepository<Profile, String> {
}

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class Profile {

	@Id
	private String id;
	private String email;
}
