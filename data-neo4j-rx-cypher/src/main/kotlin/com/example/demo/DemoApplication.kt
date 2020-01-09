package com.example.demo


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.neo4j.springframework.data.config.EnableNeo4jAuditing
import org.neo4j.springframework.data.core.ReactiveNeo4jOperations
import org.neo4j.springframework.data.core.cypher.Cypher.*
import org.neo4j.springframework.data.core.cypher.Functions.count
import org.neo4j.springframework.data.core.schema.GeneratedValue
import org.neo4j.springframework.data.core.schema.Node
import org.neo4j.springframework.data.core.support.UUIDStringGenerator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import java.time.LocalDate


@SpringBootApplication
@EnableNeo4jAuditing
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
            postRepository.deleteAll()
            postRepository.init()
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
            postRepository.findAll()

    @GetMapping("count")
    suspend fun count(): Long =
            postRepository.count()

    @GetMapping("{id}")
    suspend fun findOne(@PathVariable id: String): Post? =
            postRepository.findOne(id) ?: throw PostNotFoundException(id)

    @PostMapping("")
    suspend fun save(@RequestBody post: Post) =
            postRepository.save(post)

    @GetMapping("{id}/comments")
    fun findCommentsByPostId(@PathVariable id: String): Flow<Comment> =
            commentRepository.findByPostId(id)

    @GetMapping("{id}/comments/count")
    suspend fun countCommentsByPostId(@PathVariable id: String): Long =
            commentRepository.countByPostId(id)

    @PostMapping("{id}/comments")
    suspend fun saveComment(@PathVariable id: String, @RequestBody comment: Comment) =
            commentRepository.save(comment.copy(postId = id, content = comment.content))
}


class PostNotFoundException(postId: String) : RuntimeException("Post:$postId is not found...")

@RestControllerAdvice
class RestWebExceptionHandler {

    @ExceptionHandler(PostNotFoundException::class)
    suspend fun handle(ex: PostNotFoundException, exchange: ServerWebExchange) {

        exchange.response.statusCode = HttpStatus.NOT_FOUND
        exchange.response.setComplete().awaitFirstOrNull()
    }
}

@Component
class PostRepository(private val template: ReactiveNeo4jOperations) {

    suspend fun count(): Long =
            template.count(Post::class.java).awaitSingle()

    fun findAll(): Flow<Post> =
            template.findAll(Post::class.java).asFlow()

    suspend fun findOne(id: String): Post? =
            template.findById(id, Post::class.java).awaitFirstOrNull()

    suspend fun deleteAll(): Void? =
            template.deleteAll(Post::class.java).awaitFirstOrNull()

    suspend fun save(post: Post) =
            template.save(post).awaitSingle()


    suspend fun init() {
        save(Post(title = "My first post title", content = "Content of my first post"))
        save(Post(title = "My second post title", content = "Content of my second post"))
    }
}

@Component
class CommentRepository(private val template: ReactiveNeo4jOperations) {

    suspend fun save(comment: Comment) {
        template.save(comment).awaitSingle()
    }

    suspend fun countByPostId(postId: String): Long {
        val nodeComment = node("Comment").named("c")
        return template
                .count(
                        match(nodeComment)
                                .where(nodeComment.property("postId").isEqualTo(literalOf(postId)))
                                .returning(count(nodeComment))
                                .build(),
                        mutableMapOf()
                )
                .awaitSingle()
    }


    fun findByPostId(postId: String): Flow<Comment> {
        val nodeComment = node("Comment").named("c")
        return template
                .findAll(
                        match(nodeComment)
                                .where(nodeComment.property("postId").isEqualTo(literalOf(postId)))
                                .returning(nodeComment)
                                .build(),
                        Comment::class.java
                )
                .log()
                .asFlow()
    }
}


@Node
data class Comment(
        @Id
        @GeneratedValue(value = UUIDStringGenerator::class)
        val id: String? = null,
        val content: String? = null,
        val postId: String? = null
)

@Node
data class Post(
        @Id
        @GeneratedValue(value = UUIDStringGenerator::class)
        val id: String? = null,
        val title: String? = null,
        val content: String? = null,

        @CreatedDate
        val createdAt: LocalDate? = null,

        @LastModifiedDate
        val updatedAt: LocalDate? = null
)


