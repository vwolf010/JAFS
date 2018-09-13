package nl.v4you.jafs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import static nl.v4you.jafs.AppTest.TEST_ARCHIVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Directories {
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
        JafsFile f = vfs.getFile("/abc");
        f.createNewFile();
        assertEquals(0, f.length());
        vfs.close();
    }

    @Test
    public void localDirIndicatorWorks() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f = vfs.getFile("/a");
        f.createNewFile();
        f = vfs.getFile("/./a");
        assertTrue(f.exists());
        vfs.close();
    }

    @Test
    public void parentDirIndicatorWorks() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f = vfs.getFile("/a");
        f.mkdir();
        f = vfs.getFile("/a/b");
        f.createNewFile();
        f = vfs.getFile("/a/../a/b");
        assertTrue(f.exists());
        vfs.close();
    }

        @Test
    public void a() throws JafsException, IOException {
        Jafs vfs = new Jafs(TEST_ARCHIVE, 256, 256, 1024*1024);
        JafsFile f = vfs.getFile("/z");
        f.createNewFile();
        f = vfs.getFile("/zz");
        f.createNewFile();
        f = vfs.getFile("/zzz");
        f.createNewFile();

        f = vfs.getFile("/zz");
        f.delete();
        f = vfs.getFile("/y");
        f.createNewFile();

        f = vfs.getFile("/z");
        System.out.println(f.list().length);

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
