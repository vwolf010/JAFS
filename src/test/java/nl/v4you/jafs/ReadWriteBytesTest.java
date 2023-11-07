package nl.v4you.jafs;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static junit.framework.TestCase.assertTrue;
import static nl.v4you.jafs.AppTest.TEST_ARCHIVE;
import static org.junit.Assert.assertEquals;

public class ReadWriteBytesTest {

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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc.txt");
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc.txt");
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc.txt");
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc.txt");
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

    @Test
    public void writeSingleByte() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, 1024 * 1024);
        JafsFile f = jafs.getFile("/abc.txt");
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write('A');
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        int A = jis.read();
        jis.close();
        assertEquals('A', A);
        jafs.close();
    }

    @Ignore
    @Test
    public void adviceBlockSize() throws JafsException, IOException {
        Jafs jafs = new Jafs("/tmp/test.jafs");
        jafs.adviceBlockSize();
        jafs.close();
    }

    @Test
    public void openingOutputStreamSetsFileSizeToZero() throws JafsException, IOException {
        Jafs jafs = new Jafs(TEST_ARCHIVE, 64, 1024);
        JafsFile f = jafs.getFile("/t.bin");
        byte[] buf = new byte[650];
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(buf);
        jos.close();
        assertEquals(650, f.length());
        jos = jafs.getOutputStream(f);
        jos.close();
        assertEquals(0, f.length());
        jafs.close();
    }

    @Test
    public void appendMode() throws JafsException, IOException {
        Jafs jafs = new Jafs(TEST_ARCHIVE, 64, 1024);
        JafsFile f = jafs.getFile("/t.bin");
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write("abc".getBytes(StandardCharsets.UTF_8));
        jos.close();
        jos = jafs.getOutputStream(f, true);
        jos.write("def".getBytes(StandardCharsets.UTF_8));
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        byte[] buf = new byte[6];
        jis.read(buf);
        jis.close();
        jafs.close();
        assertEquals("abcdef",new String(buf, StandardCharsets.UTF_8));
    }

    @Ignore
    @Test
    public void writeReadBytesUsingMaxFileSize() throws JafsException, IOException {
        int blockSize = 64;
        long maxFileSize = 4_474_432;
        long bufSize = maxFileSize;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize,  maxFileSize);
        System.err.println(jafs.getINodeContext().toString());
        JafsFile f = jafs.getFile("/abc.txt");
        byte[] content = new byte[(int)bufSize];

        long blocksUsed = jafs.getSuper().getBlocksUsed();
        System.err.println("blocksUsed: " + blocksUsed);

        JafsOutputStream jos = jafs.getOutputStream(f);
        int i = 0;
        for (int n = 0; n < maxFileSize; n++) {
            content[n] = (byte)i++;
            if (i == 17) i = 0;
        }
        jos.write(content);
        jos.close();

        System.err.println("blocksUsed: " + jafs.getSuper().getBlocksUsed());

        JafsInputStream jis = jafs.getInputStream(f);
        i = 0;
        assertEquals(maxFileSize, jis.read(content));
        for (int n = 0; n < maxFileSize; n++) {
            assertEquals(content[n], (byte)i);
            i++;
            if (i == 17) i = 0;
        }
        jis.close();

        f.delete();

        System.err.println("blocksUsed: "+jafs.getSuper().getBlocksUsed());

        jafs.close();
        System.err.println(maxFileSize + " bytes written");
    }

    @Ignore
    @Test
    public void x() throws JafsException, IOException {
        Jafs jafs = new Jafs("/tmp/ggc_512_128_10MB_compressed.jafs");
        System.err.println(jafs.stats());
        jafs.close();
    }

    @Ignore
    @Test
    public void y() throws JafsException, IOException {
        int blockSize = 256;

        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, 10L * 1024L * 1024L);
        JafsFile f = jafs.getFile("/a.txt");
        byte content[] = new byte[blockSize];

        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();

        jos = jafs.getOutputStream(f);
        jos.write("abcd".getBytes());
        jos.close();

        jafs.close();
    }

    @Test
    public void initialCreationIsCorrect() throws JafsException, IOException {
        int blockSize = 128;
        long maxFileSize = 4*1024*1024;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize,  maxFileSize);
        jafs.close();
        jafs = new Jafs(TEST_ARCHIVE);
        JafsOutputStream jos = jafs.getOutputStream(jafs.getFile("/a.txt"));
        jos.write("Hallo".getBytes());
        jafs.close();
        jafs = new Jafs(TEST_ARCHIVE);
        jos = jafs.getOutputStream(jafs.getFile("/a.txt"));
        jos.write("again".getBytes());
        jafs.close();
    }
}
