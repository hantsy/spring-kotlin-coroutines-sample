package com.example.demo


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrElse
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@SpringBootApplication
class DemoApplication


fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@Component
class DataInitializer(private val postRepository: PostRepository) {

    @EventListener(value = [ApplicationReadyEvent::class])
    fun init() {
        println(" start data initialization  ...")
        runBlocking {
            val deleted = postRepository.deleteAll()
            println(" $deleted posts removed.")
            postRepository.save(Post(title = "My first post title", content = "Content of my first post"))
            postRepository.save(Post(title = "My second post title", content = "Content of my second post"))
        }

        println(" done data initialization  ...")
    }

}


@RestController
@RequestMapping("/posts")
class PostController(
        private val postRepository: PostRepository,
        private val commentRepository: CommentRepository
) {

    @GetMapping("")
    fun findAll(): Flow<Post> =
            postRepository.findAll().asFlow()

    @GetMapping("{id}")
    suspend fun findOne(@PathVariable id: Long): Post? =
            postRepository.findById(id).awaitFirstOrElse { throw PostNotFoundException(id) }

    @PostMapping("")
    suspend fun save(@RequestBody post: Post): Post =
            postRepository.save(post).awaitSingle()

    @GetMapping("{id}/comments")
    fun findCommentsByPostId(@PathVariable id: Long): Flow<Comment> =
            commentRepository.findByPostId(id).asFlow()

    @GetMapping("{id}/comments/count")
    suspend fun countCommentsByPostId(@PathVariable id: Long): Long =
            commentRepository.countByPostId(id).awaitSingle()

    @PostMapping("{id}/comments")
    suspend fun saveComment(@PathVariable id: Long, @RequestBody comment: Comment): Comment =
            commentRepository.save(comment.copy(postId = id, content = comment.content)).awaitSingle()
}


class PostNotFoundException(postId: Long) : RuntimeException("Post:$postId is not found...")

@RestControllerAdvice
class RestWebExceptionHandler {

    @ExceptionHandler(PostNotFoundException::class)
    suspend fun handle(ex: PostNotFoundException, exchange: ServerWebExchange) {

        exchange.response.statusCode = HttpStatus.NOT_FOUND
        exchange.response.setComplete().awaitFirstOrNull()
    }
}

interface PostRepository : R2dbcRepository<Post, Long> {}

//Query derivation not yet supported
//https://docs.spring.io/spring-data/r2dbc/docs/1.0.x/reference/html/#r2dbc.repositories.queries
interface CommentRepository : R2dbcRepository<Comment, Long> {
    @Query("SELECT * FROM comments WHERE post_id = $1")
    fun findByPostId(id: Long): Flux<Comment>

    @Query("select count(*) FROM comments WHERE post_id = $1")
    fun countByPostId(id: Long): Mono<Long>
}


@Table("comments")
data class Comment(@Id val id: Long? = null,
                   @Column("content") val content: String? = null,
                   @Column("post_id") val postId: Long? = null)

@Table("posts")
data class Post(@Id val id: Long? = null,
                @Column("title") val title: String? = null,
                @Column("content") val content: String? = null
)


