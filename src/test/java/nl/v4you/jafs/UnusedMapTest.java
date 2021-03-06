package nl.v4you.jafs;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static junit.framework.TestCase.assertTrue;
import static nl.v4you.jafs.AppTest.TEST_ARCHIVE;
import static org.junit.Assert.assertEquals;

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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024);
        byte content[] = new byte[blockSize];
        byte buf[] = new byte[blockSize];
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
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, blockSize, 1024 * 1024 * 1024);
        byte content[] = new byte[blockSize];
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

        int blockSize = 256;
        Jafs jafs = new Jafs(TEST_ARCHIVE, blockSize, 64, 1024 * 1024);
        byte content[] = new byte[blockSize];
        rnd.nextBytes(content);

        JafsFile f = jafs.getFile("/abc.bin");
        JafsOutputStream jos = jafs.getOutputStream(f);
        for (int i=0; i<20; i++) {
            jos.write(content);
        }
        jos.close();
        jafs.close();

        File g = new File(TEST_ARCHIVE);
        long flen1 = g.length();

        jafs = new Jafs(TEST_ARCHIVE, blockSize, 64, 1024 * 1024);
        for (int n=0; n<5; n++) {
            f = jafs.getFile("/abc.bin");
            jos = jafs.getOutputStream(f);
            for (int i=0; i<20; i++) {
                jos.write(content);
            }
            jos.close();
        }
        System.err.println(jafs.stats());
        jafs.close();

        g = new File(TEST_ARCHIVE);
        long flen2 = g.length();

        assertEquals(flen1, flen2);
    }

    @Test
    public void setUnused() throws JafsException, IOException {
        Jafs jafs = new Jafs(TEST_ARCHIVE, 128, 64, 1024 * 1024);
        JafsBlock block = jafs.getCacheBlock(0);
        int x;

        Set<Long> blockList = new TreeSet();

        for (int n = 0; n < 4; n++) {
            int mask = 0b11000000>>(n*2);
            int invMask = mask ^ 0xff;

            int old = 0;
            block.seekSet(1);
            block.writeByte(blockList, old & 0xff);
            block.writeToDiskIfNeeded();
            jafs.getUnusedMap().setAvailableForBoth(blockList, 4+n);
            jafs.getBlockCache().flushBlocks(blockList);
            blockList.clear();
            block.readFromDisk();
            block.seekSet(1);
            x = block.readByte();
            assertEquals(0, x & mask);
            assertEquals(old & invMask, x & invMask);

            old = 0b11111111;
            block.seekSet(1);
            block.writeByte(blockList, old & 0xff);
            block.writeToDiskIfNeeded();
            jafs.getUnusedMap().setAvailableForBoth(blockList, 4+n);
            jafs.getBlockCache().flushBlocks(blockList);
            blockList.clear();
            block.readFromDisk();
            block.seekSet(1);
            x = block.readByte();
            assertEquals(0, x & mask);
            assertEquals(old & invMask, x & invMask);

            old = 0;
            block.seekSet(1);
            block.writeByte(blockList, old & 0xff);
            block.writeToDiskIfNeeded();
            jafs.getUnusedMap().setAvailableForInodeOnly(blockList, 4+n);
            jafs.getBlockCache().flushBlocks(blockList);
            blockList.clear();
            block.readFromDisk();
            block.seekSet(1);
            x = block.readByte();
            assertEquals(0b01000000 >> (n*2), x & mask);
            assertEquals(old & invMask, x & invMask);

            old = 0b11111111;
            block.seekSet(1);
            block.writeByte(blockList, old & 0xff);
            block.writeToDiskIfNeeded();
            jafs.getUnusedMap().setAvailableForInodeOnly(blockList, 4+n);
            jafs.getBlockCache().flushBlocks(blockList);
            blockList.clear();
            block.readFromDisk();
            block.seekSet(1);
            x = block.readByte();
            assertEquals(0b01000000 >> (n*2), x & mask);
            assertEquals(old & invMask, x & invMask);

            old = 0;
            block.seekSet(1);
            block.writeByte(blockList, old & 0xff);
            block.writeToDiskIfNeeded();
            jafs.getUnusedMap().setAvailableForNeither(blockList, 4+n);
            jafs.getBlockCache().flushBlocks(blockList);
            blockList.clear();
            block.readFromDisk();
            block.seekSet(1);
            x = block.readByte();
            assertEquals(0b11000000 >> (n*2), x & mask);
            assertEquals(old & invMask, x & invMask);

            old = 0b11111111;
            block.seekSet(1);
            block.writeByte(blockList, old & 0xff);
            block.writeToDiskIfNeeded();
            jafs.getUnusedMap().setAvailableForNeither(blockList, 4+n);
            jafs.getBlockCache().flushBlocks(blockList);
            blockList.clear();
            block.readFromDisk();
            block.seekSet(1);
            x = block.readByte();
            assertEquals(0b11000000 >> (n*2), x & mask);
            assertEquals(old & invMask, x & invMask);
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
        byte buf[] = new byte[blockSize];
        rnd.nextBytes(buf);

        int TEST_LOOP = 10000;

        jafs = new Jafs(TEST_ARCHIVE, blockSize, 64, 1024 * 1024);

        for (int n=0; n<TEST_LOOP; n++) {

            int i = rnd.nextInt(1000);

            if (rnd.nextInt(50)==0) {
                jafs.close();
                jafs = new Jafs(TEST_ARCHIVE);
            }

            f = jafs.getFile("/" + (i*1000));

            long fileLen;
            if (f.exists()) {
                if (f.length() < 4 * blockSize) {
                    jos = jafs.getOutputStream(f, true);
                    fileLen = f.length();
                    jos.write(buf);
                    jos.close();
                    assertEquals(fileLen + blockSize, f.length());
                } else {
                    if (rnd.nextInt(100)<20) {
                        assertTrue(f.delete());
                        assertTrue(!f.exists());
                    }
                    else {
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
