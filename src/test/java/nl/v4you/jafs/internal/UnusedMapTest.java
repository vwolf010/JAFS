package nl.v4you.jafs.internal;

import nl.v4you.jafs.*;
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
import static org.junit.Assert.assertFalse;

public class UnusedMapTest {

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
    public void forceCreationOfNewUnusedMap() throws JafsException, IOException {
        int blockSize = 128;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        byte[] content = new byte[blockSize];
        byte[] buf = new byte[blockSize];
        for (int i=0; i<2*blockSize; i++) {
            JafsFile f = jafs.getFile("/abc"+i+".txt");
            JafsOutputStream jos = jafs.getOutputStream(f);
            rnd.nextBytes(content);
            jos.write(content);
            jos.close();
            JafsInputStream jis = jafs.getInputStream(f);
            jis.read(buf);
            jis.close();
            assertTrue(Arrays.equals(content, buf));
        }
        jafs.close();
    }

    @Test
    public void reuseDataBlockAsInodeBlock() throws JafsException, IOException {
        int blockSize = 128;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        byte[] content = new byte[blockSize];
        Arrays.fill(content, (byte)0xff); // if this were an inode record, all flags would be set

        JafsFile f = jafs.getFile("/abc1.txt");
        JafsOutputStream jos = jafs.getOutputStream(f);
        for (int i=0; i<10; i++) {
            // write ten blocks of ones
            jos.write(content);
        }
        jos.close();

        jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();

        // The inode block of this file should re-use the data block of the file
        // that was previously deleted.
        f = jafs.getFile("/abc2.txt");
        jos = jafs.getOutputStream(f);
        jos.write(content);
        jos.close();

        jafs.close();
    }

    @Test
    public void fileSizeStable() throws JafsException, IOException {

        int blockSize = 64;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);
        byte[] content = new byte[blockSize];
        rnd.nextBytes(content);

        JafsFile f = jafs.getFile("/abc.bin");
        JafsOutputStream jos = jafs.getOutputStream(f);
        for (int i=0; i<2; i++) {
            jos.write(content);
        }
        jos.close();
        jafs.close();

        File g = new File(TEST_ARCHIVE);
        long flen1 = g.length();

        jafs = new Jafs(TEST_ARCHIVE, blockSize);
        for (int n=0; n<5; n++) {
            f = jafs.getFile("/abc.bin");
            jos = jafs.getOutputStream(f);
            for (int i=0; i<2; i++) {
                jos.write(content);
            }
            jos.close();
        }
        System.out.println(jafs.stats());
        jafs.close();

        g = new File(TEST_ARCHIVE);
        long flen2 = g.length();

        assertEquals(flen1, flen2);
    }

    @Test
    public void setUnused() throws JafsException, IOException {
        int blockSize = 128;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize);

        JafsBlock block = jafs.getCacheBlock(0);
        for (int n = 0; n < 8; n++) {
            int mask = 0b10000000 >> n;
            int invMask = mask ^ 0xff;

            block.seekSet(blockSize + 1);
            block.writeByte(0);
            block.writeToDisk();
            JafsUnusedMap um = jafs.getUnusedMap();
            um.setAvailable(8 + n);
            jafs.getBlockCache().flushBlocks();
            block.readFromDisk();
            block.seekSet(blockSize + 1);
            assertEquals(mask, block.readByte());

            block.seekSet(blockSize + 1);
            block.writeByte(0b11111111);
            block.writeToDisk();
            um.setAvailable(8 + n);
            jafs.getBlockCache().flushBlocks();
            block.readFromDisk();
            block.seekSet(blockSize + 1);
            assertEquals(0b11111111, block.readByte());

            block.seekSet(blockSize + 1);
            block.writeByte(0b11111111);
            block.writeToDisk();
            um.setUnavailable(8 + n);
            jafs.getBlockCache().flushBlocks();
            block.readFromDisk();
            block.seekSet(blockSize + 1);
            assertEquals( invMask, block.readByte());

            block.seekSet(blockSize + 1);
            block.writeByte(0);
            block.writeToDisk();
            um.setUnavailable(8 + n);
            jafs.getBlockCache().flushBlocks();
            block.readFromDisk();
            block.seekSet(blockSize + 1);
            assertEquals( 0, block.readByte());
        }

        jafs.close();
    }

    @Ignore
    @Test
    public void randomWrites() throws JafsException, IOException {
        int blockSize = 256;
        Jafs jafs;
        JafsFile f;
        JafsOutputStream jos;
        byte[] buf = new byte[blockSize];
        rnd.nextBytes(buf);

        int TEST_LOOP = 5_000;

        jafs = new Jafs(TEST_ARCHIVE, blockSize);

        for (int n=0; n < TEST_LOOP; n++) {

            int i = rnd.nextInt(1_000);

            if (rnd.nextInt(50) == 0) {
                jafs.close();
                jafs = new Jafs(TEST_ARCHIVE);
            }

            f = jafs.getFile("/" + (i * 1000));

            long fileLen;
            if (f.exists()) {
                if (f.length() < 4 * blockSize) {
                    jos = jafs.getOutputStream(f, true);
                    fileLen = f.length();
                    jos.write(buf);
                    jos.close();
                    assertEquals(fileLen + blockSize, f.length());
                } else {
                    if (rnd.nextInt(100) < 20) {
                        assertTrue(f.delete());
                        assertFalse(f.exists());
                    } else {
                        fileLen = 0;
                        jos = jafs.getOutputStream(f);
                        jos.write(buf);
                        jos.close();
                        assertEquals(fileLen + blockSize, f.length());
                    }
                }
            } else {
                fileLen = 0;
                jos = jafs.getOutputStream(f);
                jos.write(buf);
                jos.close();
                assertEquals(fileLen + blockSize, f.length());
            }
        }
        jafs.close();

        jafs = new Jafs(TEST_ARCHIVE);
        f = jafs.getFile("/");
        for (String s : f.list()) {
            System.out.println(s);
        }
        jafs.close();
    }
}
