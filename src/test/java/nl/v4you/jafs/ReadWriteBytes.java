package nl.v4you.jafs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static junit.framework.TestCase.assertTrue;
import static nl.v4you.jafs.AppTest.TEST_ARCHIVE;
import static org.junit.Assert.assertEquals;

public class ReadWriteBytes {

    Random rnd = new Random();

    @Before
    public void doBefore() {
        File f = new File(TEST_ARCHIVE);
        if (f.exists()) {
            f.delete();
        }
    }

    @After
    public void doAfter() {
        File f = new File(TEST_ARCHIVE);
        if (f.exists()) {
            f.delete();
        }
    }

    @Test
    public void writeReadBytesInlined() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc");
        byte content[] = "ab".getBytes();
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();
        byte buf[] = new byte[10];
        JafsInputStream jis = jafs.getInputStream(f);
        int len = jis.read(buf);
        assertEquals(content.length, len);
        assertTrue(Arrays.equals(content, Arrays.copyOf(buf, content.length)));
        jafs.close();
    }

    @Test
    public void writeReadBytesBlock() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc");
        byte content[] = new byte[blockSize];
        rnd.nextBytes(content);
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        byte buf[] = new byte[content.length];
        int len = jis.read(buf);
        assertEquals(content.length, len);
        assertTrue(Arrays.equals(content, Arrays.copyOf(buf, content.length)));
        jafs.close();
    }

    @Test
    public void switchFromInlineToBlock() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc");
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write("ab".getBytes());
        byte content[] = new byte[blockSize];
        rnd.nextBytes(content);
        jos.write(content);
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        byte buf[] = new byte[content.length+2];
        int len = jis.read(buf);
        assertEquals(content.length+2, len);
        assertTrue(Arrays.equals("ab".getBytes(), Arrays.copyOf(buf, 2)));
        assertTrue(Arrays.equals(content, Arrays.copyOfRange(buf, 2,content.length+2)));
        jafs.close();
    }

    @Test
    public void writeReadBytesWithOffsetAndLength() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc");
        byte content[] = "abcdef".getBytes();
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content, 2, 2);
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        byte buf[] = new byte[10];
        int len = jis.read(buf, 1, 2);
        assertEquals(2, len);
        assertEquals('c', buf[1]);
        assertEquals('d', buf[2]);
        jafs.close();
    }
}
