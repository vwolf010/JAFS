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

public class UnusedMap {

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
            JafsFile f = jafs.getFile("/abc"+i);
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

}
