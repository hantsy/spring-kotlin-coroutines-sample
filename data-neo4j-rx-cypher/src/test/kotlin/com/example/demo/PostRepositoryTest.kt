package com.example.demo

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.neo4j.springframework.boot.test.autoconfigure.data.DataNeo4jTest
import org.neo4j.springframework.data.core.ReactiveNeo4jClient
import org.neo4j.springframework.data.core.ReactiveNeo4jOperations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean


@DataNeo4jTest
class PostRepositoryTest {

    @Autowired
    private lateinit var posts: PostRepository

    @Autowired
    private lateinit var template: ReactiveNeo4jOperations

    @Test
    fun `get all posts`() {
        val inserted: Post? = template.save(Post(title = "mytitle", content = "mycontent")).block()

        assertNotNull(inserted?.id)
        println("inserted id:$inserted.id")

        runBlocking {
            val post = posts.findOne(inserted?.id!!)
            assertEquals("mytitle", post?.title)
            assertEquals("mycontent", post?.content)
        }

    }


    @TestConfiguration
    class TestConfig {
        @Autowired
        private lateinit var client: ReactiveNeo4jOperations

        @Bean
        fun posts() = PostRepository(client)
    }

}