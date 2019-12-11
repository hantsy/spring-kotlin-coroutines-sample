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
import org.springframework.data.r2dbc.core.*
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
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
class DataInitializer(/*private val databaseClient: DatabaseClient,*/ private val postRepository: PostRepository) {

    @EventListener(value = [ApplicationReadyEvent::class])
    fun init() {
        println(" start data initialization  ...")
        /*this.databaseClient.insert()
                .into("posts")
                //.nullValue("id", Long::class.java)
                .value("title", "First post title")
                .value("content", "Content of my first post")
                .then()
                //
                .log()
                .thenMany(
                        this.databaseClient.select()
                                .from("posts")
                                .orderBy(by(desc("id")))
                                .`as`(Post::class.java)
                                .fetch()
                                .all()
                                .log()
                )
                .subscribe(null, null, { println("initialization is done...") })*/
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

    @GetMapping("{id}")
    suspend fun findOne(@PathVariable id: Long): Post? =
            postRepository.findOne(id) ?: throw PostNotFoundException(id)

    @PostMapping("")
    suspend fun save(@RequestBody post: Post) =
            postRepository.save(post)

    @GetMapping("{id}/comments")
    fun findCommentsByPostId(@PathVariable id: Long): Flow<Comment> =
            commentRepository.findByPostId(id)

    @GetMapping("{id}/comments/count")
    suspend fun countCommentsByPostId(@PathVariable id: Long): Long =
            commentRepository.countByPostId(id)

    @PostMapping("{id}/comments")
    suspend fun saveComment(@PathVariable id: Long, @RequestBody comment: Comment) =
            commentRepository.save(comment.copy(postId = id, content = comment.content))
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

@Component
class PostRepository(private val client: DatabaseClient) {

    suspend fun count(): Long =
            client.execute("SELECT COUNT(*) FROM posts")
                    .asType<Long>()
                    .fetch()
                    .awaitOne()

    fun findAll(): Flow<Post> =
            client.select()
                    .from("posts")
                    .asType<Post>()
                    .fetch()
                    .flow()

    suspend fun findOne(id: Long): Post? =
            client.execute("SELECT * FROM posts WHERE id = \$1")
                    .bind(0, id)
                    .asType<Post>()
                    .fetch()
                    .awaitOneOrNull()

    suspend fun deleteAll(): Int =
            client.execute("DELETE FROM posts")
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()

    suspend fun save(post: Post) =
            client.insert()
                    .into<Post>()
                    .table("posts")
                    .using(post)
                    .await()

    suspend fun init() {
        save(Post(title = "My first post title", content = "Content of my first post"))
        save(Post(title = "My second post title", content = "Content of my second post"))
    }
}

@Component
class CommentRepository(private val client: DatabaseClient) {

    suspend fun save(comment: Comment) =
            client.insert().into<Comment>().table("comments").using(comment).await()

    suspend fun countByPostId(postId: Long): Long =
            client.execute("SELECT COUNT(*) FROM comments WHERE post_id = \$1")
                    .bind(0, postId)
                    .asType<Long>()
                    .fetch()
                    .awaitOne()

    fun findByPostId(postId: Long): Flow<Comment> =
            client.execute("SELECT * FROM comments WHERE post_id = \$1")
                    .bind(0, postId).asType<Comment>()
                    .fetch()
                    .flow()
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


