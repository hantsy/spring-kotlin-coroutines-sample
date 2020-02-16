package com.example.demo


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.neo4j.springframework.data.core.ReactiveNeo4jClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import java.time.LocalDate
import java.util.*


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

    @PutMapping("{id}")
    suspend fun update(@PathVariable id: String, @RequestBody post: Post)=
            postRepository.save(post.copy(id=id))

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
            commentRepository.save(Comment(postId = id, content = comment.content))
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
class PostRepository(private val client: ReactiveNeo4jClient) {

    suspend fun count(): Long =
            client.query("MATCH (p:Post) RETURN count(p)")
                    .fetchAs(Long::class.java)
                    .mappedBy { ts, r -> r.get(0).asLong() }
                    .one()
                    .awaitSingle()

    fun findAll(): Flow<Post> =
            client
                    .query(
                            "MATCH (p:Post) " +
                                    "RETURN p.id as id, p.title as title, p.content as content, p.createdAt as createdAt, p.updatedAt as updatedAt"
                    )
                    .fetchAs(Post::class.java)
                    .mappedBy { ts, r ->
                        println("createdAt:" + r.get("createdAt"))
                        println("updatedAt:" + r.get("updatedAt"))
                        println("updatedAt is null:" + (r.get("updatedAt") == null))
                        Post(
                                r.get("id").asString(),
                                r.get("title").asString(),
                                r.get("content").asString(),
                                r.get("createdAt").asLocalDate(null),
                                r.get("updatedAt").asLocalDate(null)
                                // if (null != r.get("createdAt") && "NULL" != r.get("createdAt").type().name()) r.get("createdAt").asLocalDate() else null,
                                //if (null != r.get("updatedAt") && "NULL" != r.get("updatedAt").type().name()) r.get("updatedAt").asLocalDate() else null
                        )
                    }
                    .all()
                    .asFlow()

    suspend fun findOne(id: String): Post? =
            client
                    .query(
                            "MATCH (p:Post)  \n" +
                                    "WHERE p.id = \$id\n" +
                                    "RETURN p.id as id, p.title as title, p.content as content"
                    )
                    .bind(id).to("id")
                    .fetchAs(Post::class.java)
                    .mappedBy { ts, r ->
                        Post(
                                r.get("id").asString(),
                                r.get("title").asString(),
                                r.get("content").asString(),
                                r.get("createdAt").asLocalDate(null),
                                r.get("updatedAt").asLocalDate(null)
                        )
                    }
                    .one()
                    .awaitSingle()

    suspend fun deleteAll(): Int =
            client.query("MATCH (m:Post) DETACH DELETE m")
                    .run()
                    .map { it.counters().nodesDeleted() }
                    .awaitSingle()

    suspend fun save(post: Post) {
        val query = "MERGE (p:Post {id: \$id}) \n" +
                " ON CREATE SET p.createdAt=date(), p.title=\$title, p.content=\$content\n" +
                " ON MATCH SET p.updatedAt=date(), p.title=\$title, p.content=\$content\n" +
                " RETURN p.id as id, p.title as title, p.content as content, p.createdAt as createdAt, p.updatedAt as updatedAt"

        client.query(query)
                .bind(post).with {
                    mapOf("id" to (post.id
                            ?: UUID.randomUUID().toString()), "title" to post.title, "content" to post.content)
                }
                .fetchAs(Post::class.java)
                .mappedBy { ts, r -> Post(r.get("id").asString(), r.get("title").asString(), r.get("content").asString()) }
                .one()
                .awaitSingle()
    }


    suspend fun init() {
        save(Post(title = "My first post title", content = "Content of my first post"))
        save(Post(title = "My second post title", content = "Content of my second post"))
    }
}

@Component
class CommentRepository(private val client: ReactiveNeo4jClient) {

    suspend fun save(comment: Comment) {
        val query = " MERGE (c:Comment {id: \$id}) " +
                "ON CREATE SET c.createdAt=date(), c.content=\$content, c.postId=\$postId " +
                "ON MATCH SET c.updatedAt=date(), c.content=\$content, c.postId=\$postId " +
                "RETURN c.id as id, c.content as content, c.postId as postId, c.createdAt as createdAt, c.updatedAt as updatedAt"

        client.query(query)
                .bind(comment).with {
                    mapOf("id" to (comment.id
                            ?: UUID.randomUUID().toString()), "postId" to comment.postId, "content" to comment.content)
                }
                .fetchAs(Comment::class.java)
                .mappedBy { ts, r ->
                    Comment(
                            r.get("id").asString(),
                            r.get("content").asString(),
                            r.get("postId").asString(),
                            r.get("createdAt").asLocalDate(null),
                            r.get("updatedAt").asLocalDate(null)
                    )
                }
                .one()
                .awaitSingle()
    }

    suspend fun countByPostId(postId: String): Long =
            client
                    .query(
                            "MATCH (c:Comment) WHERE c.postId = \$postId " +
                                    "RETURN count(c)"
                    )
                    .bind(postId).to("postId")
                    .fetchAs(Long::class.java)
                    .mappedBy { ts, r -> r.get(0).asLong() }
                    .one()
                    .awaitSingle()

    fun findByPostId(postId: String): Flow<Comment> =
            client
                    .query(
                            "MATCH (c:Comment) WHERE c.postId = \$postId " +
                                    " RETURN c.id as id, c.content as content, c.postId as postId, c.createdAt as createdAt, c.updatedAt as updatedAt"
                    )
                    .bind(postId).to("postId")
                    .fetchAs(Comment::class.java)
                    .mappedBy { ts, r ->
                        Comment(
                                r.get("id").asString(),
                                r.get("content").asString(),
                                r.get("postId").asString(),
                                r.get("createdAt").asLocalDate(null),
                                r.get("updatedAt").asLocalDate(null)
                        )
                    }
                    .all()
                    .asFlow()
}


data class Comment(
        val id: String? = null,
        val content: String? = null,
        val postId: String? = null,
        val createdAt: LocalDate? = null,
        val updatedAt: LocalDate? = null
)

data class Post(
        val id: String? = null,
        val title: String? = null,
        val content: String? = null,
        val createdAt: LocalDate? = null,
        val updatedAt: LocalDate? = null
)


