package nl.v4you.jafs;

class ZUtil {

    static void shortToArray(byte[] b, int off, int i) {
        b[off] = (byte)((i >>> 8) & 0xff);
        b[off + 1] = (byte)(i & 0xff);
    }

    static int arrayToShort(byte[] b, int off) {
        int i = 0;
        i |= (b[off] & 0xff) << 8;
        i |= (b[off + 1] & 0xff);
        return i;
    }

    static void intToArray(byte[] b, int off, long i) {
        b[off] = (byte)((i >>> 24) & 0xffL);
        b[off + 1] = (byte)((i >>> 16) & 0xffL);
        b[off + 2] = (byte)((i >>> 8) & 0xffL);
        b[off + 3] = (byte)(i & 0xffL);
    }

    static long arrayToInt(byte[] b, int off) {
        long i = 0;
        i |= (b[off] & 0xffL) << 24;
        i |= (b[off + 1] & 0xffL) << 16;
        i |= (b[off + 2] & 0xffL) << 8;
        i |= (b[off + 3] & 0xffL);
        return i;
    }

    static void longToArray(byte[] b, int off, long i) {
        b[off] = (byte)((i >>> 56) & 0xffL);
        b[off + 1] = (byte)((i >>> 48) & 0xffL);
        b[off + 2] = (byte)((i >>> 40) & 0xffL);
        b[off + 3] = (byte)((i >>> 32) & 0xffL);
        b[off + 4] = (byte)((i >>> 24) & 0xffL);
        b[off + 5] = (byte)((i >>> 16) & 0xffL);
        b[off + 6] = (byte)((i >>> 8) & 0xffL);
        b[off + 7] = (byte)(i & 0xffL);
    }

    static long arrayToLong(byte[] b, int off) {
        long i = 0;
        i |= (b[off] & 0xffL) << 56;
        i |= (b[off + 1] & 0xffL) << 48;
        i |= (b[off + 2] & 0xffL) << 40;
        i |= (b[off + 3] & 0xffL) << 32;
        i |= (b[off + 4] & 0xffL) << 24;
        i |= (b[off + 5] & 0xffL) << 16;
        i |= (b[off + 6] & 0xffL) << 8;
        i |= (b[off + 7] & 0xffL);
        return i;
    }

    static int byteArrayIndexOf(byte[] source, byte[] target) {
        if (target.length == 0) {
            return 0;
        }

        byte first = target[0];
        int max = (source.length - target.length);

        for (int i = 0; i <= max; i++) {
            /* Look for first character. */
            if (source[i] != first) {
                while (++i <= max && source[i] != first);
            }

            /* Found first character, now look at the rest of v2 */
            if (i <= max) {
                int j = i + 1;
                int end = j + target.length - 1;
                for (int k = 1; j < end && source[j] == target[k]; j++, k++);
                if (j == end) {
                    /* Found whole string. */
                    return i;
                }
            }
        }
        return -1;
    }

    static boolean contains(byte[] str, byte[] subStr) {
        return byteArrayIndexOf(str, subStr) != -1;
    }
}
