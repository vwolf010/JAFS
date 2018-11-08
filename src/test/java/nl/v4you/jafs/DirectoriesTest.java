package nl.v4you.jafs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

import static nl.v4you.jafs.AppTest.TEST_ARCHIVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectoriesTest {
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
    public void directoryGreaterThanBlockSize() throws JafsException, IOException {
        int blockSize = 256;
        Jafs vfs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024*1024);

        LinkedList<String> a = new LinkedList<>();
        JafsFile f;
        for (int n=0; n<blockSize; n++) {
            String fname = String.format("%08d", n);
            f = vfs.getFile("/"+fname);
            a.add(fname);
            JafsOutputStream jos = vfs.getOutputStream(f);
            jos.write("hallo".getBytes());
            jos.close();
        }
        f = vfs.getFile("/");
        String names[] = f.list();
        for (String name : names) {
            a.remove(name);
        }
        assertEquals(0, a.size());

        vfs.close();
    }

    @Test
    public void fileLengthShouldBeZeroAfterCreate() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f = vfs.getFile("/abc.file");
        f.createNewFile();
        assertEquals(0, f.length());
        vfs.close();
    }

    @Test
    public void localDirIndicatorWorks() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f = vfs.getFile("/a.txt");
        f.createNewFile();
        f = vfs.getFile("/./a.txt");
        assertTrue(f.exists());
        vfs.close();
    }

    @Test
    public void aaa() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f = vfs.getFile("/11/11/11/11");
        f.mkdirs();
        f = vfs.getFile("/11/11/11/11/a.txt");
        f.createNewFile();
        assertTrue(f.exists());
        f = vfs.getFile("/22/22/22/22");
        f.mkdirs();
        f = vfs.getFile("/22/22/22/22/a.txt");
        f.createNewFile();
        assertTrue(f.exists());
        vfs.close();
    }

    @Test
    public void parentDirIndicatorWorks() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f = vfs.getFile("/a");
        f.mkdir();
        f = vfs.getFile("/a/b.txt");
        f.createNewFile();
        f = vfs.getFile("/a/../a/b.txt");
        assertTrue(f.exists());
        vfs.close();
    }

    @Test
    public void cachEntryRemovedAfterDeletingFile() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f = vfs.getFile("/a.txt");
        JafsOutputStream jos = vfs.getOutputStream(f);
        jos.write("12345".getBytes());
        jos.close();
        assertTrue(f.length()==5);
        f.delete();
        assertTrue(f.length()==0);
        vfs.close();
    }

    @Test
    public void veryLongDirectoryNameSupported() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        byte longFileName[] = new byte[512];
        Arrays.fill(longFileName, (byte)65);

        JafsFile f = vfs.getFile("/"+new String(longFileName));
        JafsOutputStream jos = vfs.getOutputStream(f);
        jos.write("12345".getBytes());
        jos.close();
        assertTrue(f.length()==5);
        vfs.close();
    }

    @Test
    public void directoryEntryReuseWorks() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f;
        f = vfs.getFile("/home");
        f.mkdir();

        f = vfs.getFile("/home/aa.txt");
        f.createNewFile();
        f = vfs.getFile("/home/bb.txt");
        f.createNewFile();
        f = vfs.getFile("/home/cc.txt");
        f.createNewFile();

        // reuse middle entry
        f = vfs.getFile("/home/bb.txt");
        f.delete();
        f = vfs.getFile("/home/dd.txt");
        f.createNewFile();
        assertTrue(f.exists());

        // reuse middle last entry
        f = vfs.getFile("/home/cc.txt");
        f.delete();
        f = vfs.getFile("/home/ee.txt");
        f.createNewFile();
        assertTrue(f.exists());

        // add to the end
        f = vfs.getFile("/home/dd.txt");
        f.delete();
        f = vfs.getFile("/home/fff.txt");
        f.createNewFile();
        assertTrue(f.exists());

        f = vfs.getFile("/home");
        assertEquals(3, f.list().length);
        assertEquals("aa.txt", f.list()[0]);
        assertEquals("ee.txt", f.list()[1]);
        assertEquals("fff.txt", f.list()[2]);

        vfs.close();
    }


//    @Test
//    public void creatingRootDirAsFileShouldNotResultInANullPointerException() throws JafsException, IOException {
//        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
//        JafsFile f = vfs.getFile("/");
//        f.createNewFile();
//    }
//
//    @Test
//    public void creatingRootDirAsDirShouldNotResultInANullPointerException() throws JafsException, IOException {
//        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
//        JafsFile f = vfs.getFile("/");
//        f.mkdir();
//    }
}
