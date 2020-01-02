package com.example.demo

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.core.ReactiveFluentMongoOperations
import org.springframework.data.mongodb.core.insert


@DataMongoTest
class PostRepositoryTest {

    @Autowired
    private lateinit var posts: PostRepository

    @Autowired
    private lateinit var mongo: ReactiveFluentMongoOperations

    @Test
    fun `get all posts`() {
        val inserted: Post? = mongo.insert<Post>()
                .one(Post(title = "mytitle", content = "mycontent"))
                .block()

        assertNotNull(inserted?.id)
        println("inserted id:$inserted.id")

        runBlocking {
            val post = posts.findOne(inserted?.id!!)
            assertEquals("mytitle", post?.title)
            assertEquals("mycontent", post?.content)
        }

    }


    @TestConfiguration
    class TestConfig{
        @Autowired
        private lateinit var mongo: ReactiveFluentMongoOperations

        @Bean
        fun posts() = PostRepository(mongo)
    }

}