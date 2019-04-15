package com.example.demo


import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort.Order.desc
import org.springframework.data.domain.Sort.by
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.function.*
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.ServerResponse.*
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import java.net.URI


@SpringBootApplication
class DemoApplication


fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@Component
class DataInitializer(/*private val databaseClient: DatabaseClient,*/ private val postRepository: PostRepository) {

    @EventListener(value = [ApplicationReadyEvent::class])
    fun init() {
        println("start data initialization  ...")
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
            //postRepository.deleteAll()
            postRepository.init()
        }

    }

}

@Configuration
@EnableR2dbcRepositories
class DatabaseConfig : AbstractR2dbcConfiguration() {

    override fun connectionFactory(): ConnectionFactory {
        return PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host("localhost")
                        .database("test")
                        .username("user")
                        .password("password")
                        .build()
        )

    }

}

@Configuration
class RouterConfiguration {

    @Bean
    fun routes(postHandler: PostHandler) = coRouter {
        "/posts".nest {
            GET("", postHandler::all)
            GET("/{id}", postHandler::get)
            POST("", postHandler::create)
            PUT("/{id}", postHandler::update)
            DELETE("/{id}", postHandler::delete)
        }

    }
}

@Component
class PostHandler(private val posts: PostRepository) {

    suspend fun all(req: ServerRequest): ServerResponse {
        return ok().bodyAndAwait(this.posts.findAll())
    }

    suspend fun create(req: ServerRequest): ServerResponse {
        val body = req.awaitBody<Post>()
        val createdPost = this.posts.save(body)
        return created(URI.create("/posts/$createdPost")).buildAndAwait()
    }

    suspend fun get(req: ServerRequest): ServerResponse {
        println("path variable::${req.pathVariable("id")}")
        val foundPost = this.posts.findOne(req.pathVariable("id").toLong())
        println("found post:$foundPost")
        return when {
            foundPost != null -> ok().bodyAndAwait(foundPost)
            else -> notFound().buildAndAwait()
        }
    }

    suspend fun update(req: ServerRequest): ServerResponse {
        val foundPost = this.posts.findOne(req.pathVariable("id").toLong())
        val body = req.awaitBody<Post>()
        return when {
            foundPost != null -> {
                this.posts.update(foundPost.copy(title = body.title, content = body.content))
                noContent().buildAndAwait()
            }
            else -> notFound().buildAndAwait()
        }


    }

    suspend fun delete(req: ServerRequest): ServerResponse {
        val deletedCount = this.posts.deleteById(req.pathVariable("id").toLong())
        println("$deletedCount posts was deleted")
        return notFound().buildAndAwait()
    }
}

class PostNotFoundException(postId: Long) : RuntimeException("Post:$postId is not found...")

@Component
@Order(-2)
class RestWebExceptionHandler : WebExceptionHandler {

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        if (ex is PostNotFoundException) {
            exchange.response.statusCode = HttpStatus.NOT_FOUND

            // marks the response as complete and forbids writing to it
            return exchange.response.setComplete()
        }
        return Mono.error(ex)
    }
}

@Component
class PostRepository(private val client: DatabaseClient) {

    suspend fun count(): Long =
            client.execute().sql("SELECT COUNT(*) FROM posts")
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
            client.execute()
                    .sql("SELECT * FROM posts WHERE id = \$1")
                    .bind(0, id)
                    .asType<Post>()
                    .fetch()
                    .awaitOneOrNull()

    suspend fun deleteById(id: Long) =
            client.execute()
                    .sql("DELETE FROM posts WHERE id = \$1")
                    .bind(0, id)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()

    suspend fun deleteAll() =
            client.execute().sql("DELETE FROM posts").fetch().rowsUpdated().awaitSingle()

    suspend fun save(post: Post) =
            client.insert()
                    .into<Post>().table("posts")
                    .using(post)
                    .map{ t, u ->
                        //println(t.get("id"))
                        t.get("id", Integer::class.java)?.toLong()
                     }
                    .awaitOne()


    suspend fun update(post: Post) =
            client.execute()
                    .sql("UPDATE posts SET title = \$2, content = \$3 WHERE id = \$1")
                    .bind(0, post.id!!)
                    .bind(1, post.title!!)
                    .bind(2, post.content!!)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()

    suspend fun init() {
        //client.execute().sql("CREATE TABLE IF NOT EXISTS posts (login varchar PRIMARY KEY, firstname varchar, lastname varchar);").await()
        val deletedCount = deleteAll()
        println(" $deletedCount posts are deleted!")
        save(Post(title = "My first post title", content = "Content of my first post"))
        save(Post(title = "My second post title", content = "Content of my second post"))
    }
}


@Component
class CommentRepository(private val client: DatabaseClient) {
    suspend fun save(comment: Comment) =
            client.insert().into<Comment>().table("comments").using(comment).await()

    suspend fun countByPostId(postId: Long): Long =
            client.execute()
                    .sql("SELECT COUNT(*) FROM comments WHERE post_id = \$1")
                    .bind(0, postId)
                    .asType<Long>()
                    .fetch()
                    .awaitOne()

    fun findByPostId(postId: Long): Flow<Comment> =
            client.execute()
                    .sql("SELECT * FROM comments WHERE post_id = \$1")
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


