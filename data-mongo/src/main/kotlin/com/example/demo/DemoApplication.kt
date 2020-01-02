package com.example.demo


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.*
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange


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
    suspend fun saveComment(@PathVariable id: Long, @RequestBody comment: Comment) =
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
class PostRepository(private val mongo: ReactiveFluentMongoOperations) {

    suspend fun count(): Long =
            mongo.query<Post>().count().awaitSingle()

    fun findAll(): Flow<Post> =
            mongo.query<Post>().flow()

    suspend fun findOne(id: String): Post? =
            mongo.query<Post>()
                    .matching(query(where("id").isEqualTo(id))).awaitOne()

    suspend fun deleteAll(): Long =
            mongo.remove<Post>().allAndAwait().deletedCount

    suspend fun save(post: Post) =
            mongo.insert<Post>().oneAndAwait(post)

    suspend fun update(post: Post) =
            mongo.update<Post>()
//                    .matching(query(where("id").isEqualTo(id)))
//                    .apply(Update.update("title",post.title!!).set("content", post.content!!))
                    .replaceWith(post)
                    .asType<Post>().findReplaceAndAwait()

    suspend fun init() {
        save(Post(title = "My first post title", content = "Content of my first post"))
        save(Post(title = "My second post title", content = "Content of my second post"))
    }
}

@Component
class CommentRepository(private val mongo: ReactiveFluentMongoOperations) {

    suspend fun save(comment: Comment) =
            mongo.insert<Comment>().oneAndAwait(comment)

    suspend fun countByPostId(postId: String): Long =
            mongo.query<Comment>()
                    .matching(query(where("postId").isEqualTo(postId)))
                    .count().awaitSingle()

    fun findByPostId(postId: String): Flow<Comment> =
            mongo.query<Comment>()
                    .matching(query(where("postId").isEqualTo(postId))).flow()
}


@Document
data class Comment(
        @Id val id: String? = null,
        val content: String? = null,
        val postId: Long? = null
)

@Document
data class Post(
        @Id val id: String? = null,
        val title: String? = null,
        val content: String? = null
)


