package com.inkreader.core.util

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `calculateHash should return correct SHA-256 hash`() {
        val file = tempFolder.newFile("test.txt")
        file.writeText("InkReader Test Content")
        
        // printf "InkReader Test Content" | sha256sum
        // 1007ad8bde93ab7beeae54805dbb74d585117406838bd1b8f29ca0bb9e923329
        val expectedHash = "1007ad8bde93ab7beeae54805dbb74d585117406838bd1b8f29ca0bb9e923329"
        
        val actualHash = FileUtils.calculateHash(file)
        
        assertEquals(expectedHash, actualHash)
    }
}
