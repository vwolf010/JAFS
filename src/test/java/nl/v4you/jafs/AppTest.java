package nl.v4you.jafs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
public class AppTest {

    static final String TEST_PATH = "/tmp";
    public static final String TEST_ARCHIVE = TEST_PATH + "/test.jafs";

    Random rnd = new Random();

    int blockSize;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { 64},
                { 128},
                { 256},
                { 1024},
                { 2048},
                { 4096},
                //The first item in the array is the input, and second is the expected outcome.
        });
    }

    public AppTest(int blockSize) {
        this.blockSize = blockSize;
    }

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
    public void fileLengthAndContentIsCorrectInlineFile() throws Exception {
        createAndCheckFileLengthAndContent(1);
    }

    @Test
    public void fileLengthAndContentIsCorrectSingleBlockFile() throws Exception {
        createAndCheckFileLengthAndContent(blockSize/2);
    }

    @Test
    public void fileLengthAndContentIsCorrectDualBlockFile() throws Exception {
        createAndCheckFileLengthAndContent(blockSize + blockSize/2 + 3);
    }

//    @Test
//    public void fileLengthAndContentIsCorrectBigFile() throws Exception {
//        createAndCheckFileLengthAndContent((JafsBlockCache.+5)*blockSize+4);
//    }

    @Test
    public void fileInputStreamReturnsMinusOneWhenNoMoreToRead() throws Exception {
        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/content.txt");

        JafsOutputStream fos = vfs.getOutputStream(f);
        fos.write("1234567890".getBytes());
        fos.close();

        byte[] buf = new byte[(int)f.length()];
        JafsInputStream fis = vfs.getInputStream(f);
        int bread = fis.read(buf);
        assertEquals(f.length(), bread);
        bread = fis.read(buf);
        assertEquals(-1, bread);
        fis.close();

        vfs.close();
    }

    @Test
    public void fileLengthIsCorrectAfterRename() throws Exception {
        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/content.txt");

        JafsOutputStream fos = vfs.getOutputStream(f);
        fos.write("1234567890".getBytes());
        fos.close();

        JafsFile g = vfs.getFile("/content.new");
        f.renameTo(g);

        assertEquals(10, g.length());
        vfs.close();
    }

    @Test
    public void fileInputStream() throws Exception {
        byte[] content = "12345678901234567890123456789012345678901234567890".getBytes();

        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/content.txt");

        JafsOutputStream fos = vfs.getOutputStream(f);
        fos.write(content);
        fos.close();

        byte[] buf = new byte[(int)f.length()];
        JafsInputStream fis = vfs.getInputStream(f);
        fis.read(buf);
        fis.close();

        assertTrue(Arrays.equals(content, buf));

        buf = new byte[1000];
        fis = vfs.getInputStream(f);
        int bread = fis.read(buf);
        fis.close();

        assertTrue(Arrays.equals(content, Arrays.copyOf(buf, bread)));

        buf = new byte[1000];
        fis = vfs.getInputStream(f);
        bread = fis.read(buf, 0, (int)f.length());
        fis.close();

        assertTrue(Arrays.equals(content, Arrays.copyOf(buf, bread)));

        vfs.close();
    }

    @Test
    public void createDirectories() throws Exception {
        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/sub1");
        assertFalse(f.exists());
        f.mkdir();
        assertTrue(f.exists());

        f = vfs.getFile("/sub1/sub2");
        assertFalse(f.exists());
        f.mkdir();
        assertTrue(f.exists());

        f = vfs.getFile("/sub1/sub2/sub3");
        assertFalse(f.exists());
        f.mkdir();
        assertTrue(f.exists());

        vfs.close();
    }

    @Test
    public void createFileInsideDirectories() throws Exception {
        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/sub1");
        f.mkdir();

        f = vfs.getFile("/sub1/sub2");
        f.mkdir();

        f = vfs.getFile("/sub1/sub2/sub3");
        f.mkdir();

        byte[] content = "1234567890".getBytes();

        f = vfs.getFile("/sub1/sub2/sub3/test123.file");
        JafsOutputStream fos = vfs.getOutputStream(f);
        fos.write(content);

        assertTrue(f.exists());

        byte[] buf = new byte[(int)f.length()];
        JafsInputStream fis = vfs.getInputStream(f);
        fis.read(buf, 0, (int)f.length());

        assertTrue(Arrays.equals(content, buf));

        fos.close();

        vfs.close();
    }

    @Test
    public void deleteDirectory() throws Exception {
        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/sub1");
        f.mkdir();
        f = vfs.getFile("/sub2");
        f.mkdir();
        f = vfs.getFile("/sub3");
        f.mkdir();

        f = vfs.getFile("/");
        assertEquals(3, f.list().length);

        f = vfs.getFile("/sub2");
        f.delete();

        f = vfs.getFile("/sub1");
        assertTrue(f.exists());
        f = vfs.getFile("/sub2");
        assertFalse(f.exists());
        f = vfs.getFile("/sub3");
        assertTrue(f.exists());

        f = vfs.getFile("/");
        assertEquals(2, f.list().length);

        vfs.close();
    }

    @Test
    public void fileLengthIsCorrectAfterOverwrite() throws Exception {
        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/content.txt");

        JafsOutputStream fos = vfs.getOutputStream(f);
        fos.write("1234567890".getBytes());
        fos.close();

        fos = vfs.getOutputStream(f, false);
        fos.write("12345".getBytes());
        fos.close();

        assertEquals(5, f.length());
        vfs.close();
    }

    @Test
    public void fileLengthIsCorrectAfterAppend() throws Exception {
        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/content.txt");

        JafsOutputStream fos = vfs.getOutputStream(f);
        fos.write("1234567890".getBytes());
        fos.close();

        fos = vfs.getOutputStream(f, true);
        fos.write("12345".getBytes());
        fos.close();

        assertEquals(15, f.length());
        vfs.close();
    }

    private void createAndCheckFileLengthAndContent(int i) throws IOException, JafsException {
        byte[] content = new byte[i];

        rnd.nextBytes(content);

        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsFile f = vfs.getFile("/content.txt");

        JafsOutputStream fos = vfs.getOutputStream(f);
        fos.write(content);
        fos.close();

        assertEquals(content.length, f.length());

        byte[] buf = new byte[(int)f.length()];
        JafsInputStream fis = vfs.getInputStream(f);
        fis.read(buf);
        fis.close();

        assertTrue(Arrays.equals(content, buf));

        vfs.close();
    }
}
