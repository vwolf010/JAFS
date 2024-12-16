package nl.v4you.jafs;

import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class ZUtilTest {

    @Test
    public void convertInt() {
        byte[] b = new byte[4];

        int i = 0x01020304;
        ZUtil.intToArray(b, 0, i);
        assertEquals(i, ZUtil.arrayToInt(b, 0));

        i = 0xfffefdfc;
        ZUtil.intToArray(b, 0, i);
        assertEquals(i, (int) ZUtil.arrayToInt(b, 0));
    }

    @Test
    public void convertShort() {
        byte[] b = new byte[4];

        int i = 0x0102;
        ZUtil.shortToArray(b, 0, i);
        assertEquals(i, ZUtil.arrayToShort(b, 0));

        i = 0xfffe;
        ZUtil.shortToArray(b, 0, i);
        assertEquals(i, ZUtil.arrayToShort(b, 0));
    }

    @Test
    public void byteArrayContains() {
        assertTrue(ZUtil.contains("abcdefg".getBytes(), "abc".getBytes()));
        assertTrue(ZUtil.contains("abcdefg".getBytes(), new byte[0]));
        assertTrue(ZUtil.contains(new byte[0], new byte[0]));
        assertFalse(ZUtil.contains("abcdefg".getBytes(), "abx".getBytes()));
        assertFalse(ZUtil.contains("abcdefg".getBytes(), "xyz".getBytes()));
        assertFalse(ZUtil.contains("ab".getBytes(), "abcd".getBytes()));
    }
}
