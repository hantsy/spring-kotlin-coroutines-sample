package com.example.demo

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.data.r2dbc.core.DatabaseClient
import org.springframework.data.r2dbc.core.awaitOne
import org.springframework.data.r2dbc.core.into

@DataR2dbcTest
@Disabled
class PostRepositoryTest {

    @Autowired
    private lateinit var posts: PostRepository

    @Autowired
    private lateinit var client: DatabaseClient

    @Test
    fun `get all posts`() {
        val inserted = client.insert().into<Post>().table("posts")
                .using(Post(title = "mytitle", content = "mycontent"))
                .map { row, rowMetadata -> row.get(0) as Int}
                .one().block()

        println("inserted id:$inserted")

        runBlocking {
            val post = posts.findById(inserted!!.toLong()).awaitSingle()
            assertEquals("mytitle", post?.title)
            assertEquals("mycontent", post?.content)
        }

    }

}