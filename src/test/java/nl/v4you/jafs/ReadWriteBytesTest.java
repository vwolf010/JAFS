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
import static org.junit.Assert.assertArrayEquals;
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        JafsFile f = jafs.getFile("/abc.txt");
        byte[] content = "ab".getBytes();
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();
        byte[] buf = new byte[10];
        JafsInputStream jis = jafs.getInputStream(f);
        int len = jis.read(buf);
        assertEquals(content.length, len);
        assertTrue(Arrays.equals(content, Arrays.copyOf(buf, content.length)));
        jafs.close();
    }

    @Test
    public void writeReadBytesBlock() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        JafsFile f = jafs.getFile("/abc.txt");
        byte[] content = new byte[blockSize];
        rnd.nextBytes(content);
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        byte[] buf = new byte[content.length];
        int len = jis.read(buf);
        assertEquals(content.length, len);
        assertTrue(Arrays.equals(content, Arrays.copyOf(buf, content.length)));
        jafs.close();
    }

    private void assertContent(Jafs jafs, JafsFile f, byte[] content) throws JafsException, IOException {
        byte[] buf = new byte[content.length];
        JafsInputStream jis = jafs.getInputStream(f);
        jis.read(buf);
        jis.close();
        assertArrayEquals(content, buf);
    }

    @Test
    public void overWrite() throws JafsException, IOException {
        // write 2 blocks to f1.txt -> super - unused - rootDir - inode f1 - data f1 - data f1
        // write 1 block to f1.txt  -> super - unused - rootDir - inode f1 - data f1 - available block
        // write 10 bytes to f2.txt -> super - unused - rootDir - inode f1 - data f1 - inode f2/inlined data
        // write 10 bytes to f3.txt -> super - unused - rootDir - inode f1 - data f1 - inode f2/inlined data - inode f3/inlined data
        // total blocks in the end: 7
        int blockSize = 4096;

        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = jafs.getFile("/f1.txt");
        byte[] content = new byte[blockSize];
        JafsOutputStream jos = jafs.getOutputStream(f);
        Arrays.fill(content, (byte)1);
        jos.write(content);
        jos.write(content);
        jos.close();

        assertEquals(2 * blockSize, f.length());

        Arrays.fill(content, (byte)2);
        jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();

        //assertEquals(blockSize, f.length());
        jafs.close();

        File g = new File(TEST_ARCHIVE);
        long len = g.length();

        jafs = new Jafs(TEST_ARCHIVE);

        f = jafs.getFile("/f2.txt");
        Arrays.fill(content, (byte)3);
        jos = jafs.getOutputStream(f);
        jos.write(content, 0, 10);
        jos.close();

        f = jafs.getFile("/f3.txt");
        Arrays.fill(content, (byte)4);
        jos = jafs.getOutputStream(f);
        jos.write(content, 0, 10);
        jos.close();

        byte[] buf = new byte[10];
        f = jafs.getFile("/f1.txt");
        Arrays.fill(buf, (byte)2);
        assertEquals(blockSize, f.length());
        assertContent(jafs, f, buf);
        f = jafs.getFile("/f2.txt");
        Arrays.fill(buf, (byte)3);
        assertEquals(10, f.length());
        assertContent(jafs, f, buf);
        f = jafs.getFile("/f3.txt");
        Arrays.fill(buf, (byte)4);
        assertEquals(10, f.length());
        assertContent(jafs, f, buf);

        jafs.close();

        g = new File(TEST_ARCHIVE);
        assertEquals(7 * blockSize, g.length());
    }

    @Test
    public void overWriteRedoInlined() throws JafsException, IOException {
        // write 2 blocks to f1.txt -> super - unused - rootDir - inode f1 - data f1 - data f1
        // write 1 byte to f1.txt   -> super - unused - rootDir - inode f1/inlined data - available block - available block
        // write 1 block to f2.txt  -> super - unused - rootDir - inode f1/inlined data - inode f2 - data f2
        // write 1 byte to f3.txt   -> super - unused - rootDir - inode f1/inlined data - inode f2 - data f2 - inode f3/inlined data
        // total blocks in the end: 7
        int blockSize = 4096;

        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = jafs.getFile("/f1.txt");
        byte[] content = new byte[blockSize];
        JafsOutputStream jos = jafs.getOutputStream(f);
        Arrays.fill(content, (byte)1);
        jos.write(content);
        jos.write(content);
        jos.close();

        assertEquals(2 * blockSize, f.length());

        jos = jafs.getOutputStream(f);
        Arrays.fill(content, (byte)2);
        jos.write(content, 0, 10);
        jos.close();

        //assertEquals(blockSize, f.length());
        jafs.close();

        File g = new File(TEST_ARCHIVE);
        long len = g.length();

        jafs = new Jafs(TEST_ARCHIVE);

        f = jafs.getFile("/f2.txt");
        Arrays.fill(content, (byte)3);
        jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();

        f = jafs.getFile("/f3.txt");
        Arrays.fill(content, (byte)4);
        jos = jafs.getOutputStream(f);
        jos.write(content, 0, 10);
        jos.close();

        byte[] buf = new byte[10];
        f = jafs.getFile("/f1.txt");
        Arrays.fill(buf, (byte)2);
        assertEquals(10, f.length());
        assertContent(jafs, f, buf);
        f = jafs.getFile("/f2.txt");
        Arrays.fill(buf, (byte)3);
        assertEquals(blockSize, f.length());
        assertContent(jafs, f, buf);
        f = jafs.getFile("/f3.txt");
        Arrays.fill(buf, (byte)4);
        assertEquals(10, f.length());
        assertContent(jafs, f, buf);

        jafs.close();

        g = new File(TEST_ARCHIVE);
        assertEquals(7 * blockSize, g.length());
    }

    @Test
    public void switchFromInlineToBlock() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        JafsFile f = jafs.getFile("/abc.txt");
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write("ab".getBytes());
        byte[] content = new byte[blockSize];
        rnd.nextBytes(content);
        jos.write(content);
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        byte[] buf = new byte[content.length+2];
        int len = jis.read(buf);
        assertEquals(content.length+2, len);
        assertTrue(Arrays.equals("ab".getBytes(), Arrays.copyOf(buf, 2)));
        assertTrue(Arrays.equals(content, Arrays.copyOfRange(buf, 2,content.length+2)));
        jafs.close();
    }

    @Test
    public void writeReadBytesWithOffsetAndLength() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        JafsFile f = jafs.getFile("/abc.txt");
        byte[] content = "abcdef".getBytes();
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content, 2, 2);
        jos.close();
        JafsInputStream jis = jafs.getInputStream(f);
        byte[] buf = new byte[10];
        int len = jis.read(buf, 1, 2);
        assertEquals(2, len);
        assertEquals('c', buf[1]);
        assertEquals('d', buf[2]);
        jafs.close();
    }

    void assertBlocks(Jafs jafs, long used, long total) {
        assertEquals(used, jafs.getBlocksUsed());
        assertEquals(total, jafs.getBlocksTotal());
    }

    @Test
    public void switchBackToInlined() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        assertBlocks(jafs, 2, 2);

        JafsFile f = jafs.getFile("/abc.txt");

        byte[] content = new byte[4 * blockSize + 20];
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();
        assertBlocks(jafs, 8, 8);
        assertEquals(4 * blockSize + 20, f.length());

        content = new byte[2];
        content[0] = 19;
        content[1] = 29;

        jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();
        assertBlocks(jafs, 3, 8);
        assertEquals(2, f.length());

        JafsInputStream jis = jafs.getInputStream(f);
        Arrays.fill(content, (byte)0);
        jis.read(content);
        assertEquals(19, content[0]);
        assertEquals(29, content[1]);

        jafs.close();
    }

    @Test
    public void createEmptyFile() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        assertBlocks(jafs, 2, 2); // unusedMap + rootDir

        JafsFile f = jafs.getFile("/abc.txt");
        f.createNewFile();
        assertBlocks(jafs, 2, 2); // unusedMap + rootDir
        assertTrue(f.exists());
        assertEquals(0, f.length());
    }

    @Test
    public void createDirAndFile() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        assertBlocks(jafs, 2, 2); // unusedMap + rootDir

        JafsFile d = jafs.getFile("/111");
        assertTrue(d.mkdir());
        assertTrue(d.exists());
        assertBlocks(jafs, 2, 2); // unusedMap + rootDir

        JafsFile f = jafs.getFile("/111/abc.txt");
        f.createNewFile();
        assertBlocks(jafs, 3, 3); // unusedMap + rootDir + dir /111
        assertEquals(0, f.length());

        JafsOutputStream os = jafs.getOutputStream(f);
        os.write(1);
        os.close();
        assertBlocks(jafs, 4, 4); // unusedMap + rootDir + dir /111 + file /111/abc.txt
        assertEquals(1, f.length());
    }

    @Test
    public void outputStreamEmptyByteArray() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        assertBlocks(jafs, 2, 2); // unusedMap + rootDir

        JafsFile f = jafs.getFile("/abc.txt");
        JafsOutputStream os = jafs.getOutputStream(f);
        os.write(new byte[0]);
        os.close();
        assertBlocks(jafs, 2, 2); // unusedMap + rootDir
        assertEquals(0, f.length());
    }

    @Test
    public void outputStreamDeleteInode() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        assertBlocks(jafs, 2, 2);

        JafsFile f = jafs.getFile("/abc.txt");
        JafsOutputStream jos = jafs.getOutputStream(f);
        jos.write(new byte[5]);
        jos.close();
        assertBlocks(jafs, 3, 3);
        assertEquals(5, f.length());

        jos = jafs.getOutputStream(f);
        jos.close();
        assertBlocks(jafs, 2, 3);
        assertEquals(0, f.length());
    }

    @Test
    public void writeSingleByte() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, 64);
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, 64);
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
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

        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
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
