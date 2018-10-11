package nl.v4you.jafs;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize/2, 1024 * 1024);
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

    @Ignore
    @Test
    public void writeReadBytesUsingMaxFileSize() throws JafsException, IOException {
        int blockSize = 128;
        int inodeSize = 64;
        long maxFileSize = 4330000;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, inodeSize,  maxFileSize);
        System.err.println(jafs.getINodeContext().toString());
        JafsFile f = jafs.getFile("/abc.txt");
        byte content[] = new byte[blockSize];

        long blocksUsed = jafs.getSuper().getBlocksUsed();
        System.err.println("blocksUsed: "+blocksUsed);

        JafsOutputStream jos = jafs.getOutputStream(f);
        long len = 0;
        int i = 0;
        while (len+blockSize<=maxFileSize) {
            for (int n=0; n<blockSize; n++) {
                content[n] = (byte)i++;
                if (i==17) i=0;
            }
            len += blockSize;
            jos.write(content);
        }
        jos.close();

        System.err.println("blocksUsed: "+jafs.getSuper().getBlocksUsed());

        JafsInputStream jis = jafs.getInputStream(f);
        len = 0;
        i = 0;
        while (len+blockSize<maxFileSize) {
            assertEquals(blockSize, jis.read(content));
            for (int n=0; n<blockSize; n++) {
                if (content[n]!=(byte)i) {
                    assertTrue(false);
                }
                i++;
                if (i==17) i=0;
            }
            len += blockSize;
        }
        jis.close();

        f.delete();

        System.err.println("blocksUsed: "+jafs.getSuper().getBlocksUsed());

        jafs.close();
        System.err.println(len+" bytes written");
    }

    @Ignore
    @Test
    public void x() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs("c:/data/ggc/ggc_1024_128_10MB.jafs");
        JafsFile f = jafs.getFile("/e3/ff/b9/e3/419540539.xml");
        byte content[] = new byte[256];
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();
        jafs.close();
    }

    @Ignore
    @Test
    public void y() throws JafsException, IOException {
        int blockSize = 256;

        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize/2, 10L * 1024L * 1024L);
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
}
