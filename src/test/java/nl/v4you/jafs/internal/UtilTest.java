package nl.v4you.jafs.internal;

import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class UtilTest {

    @Test
    public void convertInt() {
        byte[] b = new byte[4];

        int i = 0x01020304;
        Util.intToArray(b, 0, i);
        assertEquals(i, Util.arrayToInt(b, 0));

        i = 0xfffefdfc;
        Util.intToArray(b, 0, i);
        assertEquals(i, (int)Util.arrayToInt(b, 0));
    }

    @Test
    public void convertShort() {
        byte[] b = new byte[4];

        int i = 0x0102;
        Util.shortToArray(b, 0, i);
        assertEquals(i, Util.arrayToShort(b, 0));

        i = 0xfffe;
        Util.shortToArray(b, 0, i);
        assertEquals(i, Util.arrayToShort(b, 0));
    }

    @Test
    public void byteArrayContains() {
        assertTrue(Util.contains("abcdefg".getBytes(), "abc".getBytes()));
        assertTrue(Util.contains("abcdefg".getBytes(), new byte[0]));
        assertTrue(Util.contains(new byte[0], new byte[0]));
        assertFalse(Util.contains("abcdefg".getBytes(), "abx".getBytes()));
        assertFalse(Util.contains("abcdefg".getBytes(), "xyz".getBytes()));
        assertFalse(Util.contains("ab".getBytes(), "abcd".getBytes()));
    }
}
