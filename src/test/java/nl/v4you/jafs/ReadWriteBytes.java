package nl.v4you.jafs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static junit.framework.TestCase.assertTrue;
import static nl.v4you.jafs.AppTest.TEST_ARCHIVE;
import static org.junit.Assert.assertEquals;

public class ReadWriteBytes {

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
    public void writeReadBytes() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc");
        byte content[] = "abcd".getBytes();
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        byte buf[] = new byte[10];
        int len = jis.read(buf);
        assertEquals(4, len);
        assertTrue(Arrays.equals(content, Arrays.copyOf(buf, 4)));
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
