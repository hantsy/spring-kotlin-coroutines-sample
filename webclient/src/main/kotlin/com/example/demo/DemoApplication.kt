package com.example.demo


import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.builder
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange


@SpringBootApplication
class DemoApplication


fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@Configuration
class WebClientConfiguration {

    @Bean
    fun webClient() = builder().baseUrl("http://localhost:8080").build()

}

@RestController
@RequestMapping("/posts")
class PostController(private val client: WebClient) {
    @GetMapping("")
    suspend fun findAll() =
            client.get()
                    .uri("/posts")
                    .accept(MediaType.APPLICATION_JSON)
                    .awaitExchange()
                    .awaitBody<Any>()


/*
    @GetMapping("")
    suspend fun findAll(): Flow<Post> =
            client.get()
                    .uri("/posts")
                    .accept(MediaType.APPLICATION_JSON)
                    .awaitExchange()
                    .awaitBody()

*/

    @GetMapping("/{id}")
    suspend fun findOne(@PathVariable id: Long): PostDetails = withDetails(id)


    private suspend fun withDetails(id: Long): PostDetails {
        val post =
                client.get().uri("/posts/$id")
                        .accept(APPLICATION_JSON)
                        .awaitExchange().awaitBody<Post>()

        val count =
                client.get().uri("/posts/$id/comments/count")
                        .accept(APPLICATION_JSON)
                        .awaitExchange().awaitBody<Long>()

        return PostDetails(post, count)
    }

}

@RestController
@RequestMapping("/posts2")
class Post2Controller(private val client: WebClient) {


    @GetMapping("/{id}")
    suspend fun findOne(@PathVariable id: Long): PostDetails = withDetails(id)


    private suspend fun withDetails(id: Long): PostDetails = coroutineScope {
        val post = async {
            client.get().uri("/posts/$id")
                    .accept(APPLICATION_JSON)
                    .awaitExchange().awaitBody<Post>()
        }
        val count = async {
            client.get().uri("/posts/$id/comments/count")
                    .accept(APPLICATION_JSON)
                    .awaitExchange().awaitBody<Long>()
        }
        PostDetails(post.await(), count.await())
    }

}

data class PostDetails(
        val details: Post? = null,
        val countOfComments: Long? = null
)

data class Post(val id: Long? = null,
                val title: String? = null,
                val content: String? = null
)


