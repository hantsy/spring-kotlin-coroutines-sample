# Using Kotlin Coroutines with Spring
Before Spring 5.2,  you can experience Kotlin Coroutines by the effort from community, eg. [spring-kotlin-coroutine ](https://github.com/konrad-kaminski/spring-kotlin-coroutine) on Github. There are several features introduced in Spring 5.2,  besides [the functional programming style introduced in Spring MVC](https://github.com/hantsy/spring-webmvc-functional-sample), another attractive feature is that Kotlin Coroutines is finally get official support.

Kotlin coroutines provides an alternative approach to  write asynchronous applications with Spring Reactive stack, but in an imperative code style.

In this post, I will rewrite [my reactive sample](https://github.com/hantsy/spring-reactive-sample) in Kotlin Coroutines.

Generate a Spring Boot project using [Spring initializr](https://start.spring.io).  

* Language: Kotlin 
* Spring Boot version : 2.2.0.BUILD-SNAPSHOT
* Dependencies: Web Reactive

Open the *pom.xml* file , add some modification. 

Add kotlin-coroutines related dependencies in the *dependencies* section.

```xml
<dependency>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kotlinx-coroutines-core</artifactId>
    <version>${kotlinx-coroutines.version}</version>
</dependency>
<dependency>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kotlinx-coroutines-reactor</artifactId>
    <version>${kotlinx-coroutines.version}</version>
</dependency>
```

Define  a **kotlin-coroutines.version** in the properties.

```xml
<kotlinx-coroutines.version>1.2.0</kotlinx-coroutines.version>
```

Currently  Spring Data R2DBC added basic coroutines support in `DatabaseClient`,  here we use Spring Data R2DBC for data operations.

Add  Spring Data R2DBC related dependencies, and use PostgresSQL as backend database.

```xml
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-r2dbc</artifactId>
    <version>${spring-data-r2dbc.version}</version>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>

<dependency>
    <groupId>io.r2dbc</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
</dependency>
```

Currently the coroutines feature is only provided in Spring Data R2DBC **1.0.0.BUILD-SNAPSHOT**, declare a `spring-data-r2dbc.version` property.

```xml
 <spring-data-r2dbc.version>1.0.0.BUILD-SNAPSHOT</spring-data-r2dbc.version>
```

Enables  R2dbc support by subclassing `AbstractR2dbcConfiguration`.

```java
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
```



Create a data class mapping to the table `posts`.

```java
@Table("posts")
data class Post(@Id val id: Long? = null,
                @Column("title") val title: String? = null,
                @Column("content") val content: String? = null
)
```

Follow the [Reactive stack to Kotlin Coroutines translation guide](https://docs.spring.io/spring/docs/5.2.0.M1/spring-framework-reference/languages.html#how-reactive-translates-to-coroutines) provided in Spring reference documentation, create a repository class for Post.

```java
@Component
class PostRepository(private val client: DatabaseClient) {

    suspend fun count(): Long =
            client.execute().sql("SELECT COUNT(*) FROM posts")
                    .asType<Long>().fetch().awaitOne()

    fun findAll(): Flow<Post> =
            client.select().from("posts").asType<Post>().fetch().flow()

    suspend fun findOne(id: Long): Post? =
            client.execute()
                    .sql("SELECT * FROM posts WHERE id = \$1")
                    .bind(0, id).asType<Post>()
                    .fetch()
                    .awaitOneOrNull()

    suspend fun deleteAll() =
            client.execute()
                    .sql("DELETE FROM posts")
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
```

Here is the controller for Post.

```java
@RestController
@RequestMapping("/posts")
class PostController(
        private val postRepository: PostRepository
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

}
```

You can also initialize data in a `CommandLineRunner`bean or listen the `@ApplicationReadyEvent`, use a `runBlocking` to wrap the suspend functions.

```kot
runBlocking {
     val deleted = postRepository.deleteAll()
     println(" $deleted posts was cleared.")
     postRepository.init()
}
```

Run the application, it works as well as [the previous Reactive examples](https://github.com/hantsy/spring-reactive-sample).

You can also convert your router DSL to Kotlin  Coroutines using the new coRouter DSL.

```ko
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
```

Like the changes in the controller, the `PostHandler` can be written in an imperative way.

```kot
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
```

Besides annotated controllers and functional router DSL get Kotlin Coroutines support, Spring `WebClient` also embrace Kotlin Coroutines.

```kot
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

```

In the `withDetails` method, post and count are called one by one in a sequence.  If you want to call them in a parallel way,   try to use `async` context to wrap every calls, and put all tasks in a `coroutineScope` context. 

```kot
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
```

Check out the [codes](https://github.com/hantsy/spring-kotlin-coroutines-sample) from Github.













