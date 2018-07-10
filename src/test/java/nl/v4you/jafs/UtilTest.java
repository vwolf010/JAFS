package nl.v4you.jafs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilTest {

    @Test
    public void convertInt() {
        byte b[] = new byte[4];

        int i = 0x01020304;
        Util.intToArray(b, i);
        assertEquals(i, Util.arrayToInt(b));

        i = 0xfffefdfc;
        Util.intToArray(b, i);
        assertEquals(i, Util.arrayToInt(b));
    }

    @Test
    public void convertShort() {
        byte b[] = new byte[4];

        int i = 0x0102;
        Util.shortToArray(b, i);
        assertEquals(i, Util.arrayToShort(b));

        i = 0xfffe;
        Util.shortToArray(b, i);
        assertEquals(i, Util.arrayToShort(b));
    }
}
