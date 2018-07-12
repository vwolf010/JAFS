package nl.v4you.jafs;

public class Util {

    static void shortToArray(byte b[], int off, int i) {
        b[off] = (byte)((i>>8) & 0xff);
        b[off+1] = (byte)(i & 0xff);
    }

    static int arrayToShort(byte b[], int off) {
        int i=0;
        i |= (b[off] & 0xff) <<8;
        i |= (b[off+1] & 0xff);
        return i;
    }

    static void intToArray(byte b[], int off, long i) {
        b[off] = (byte)((i>>24) & 0xffL);
        b[off+1] = (byte)((i>>16) & 0xffL);
        b[off+2] = (byte)((i>>8) & 0xffL);
        b[off+3] = (byte)(i & 0xffL);
    }

    static long arrayToInt(byte b[], int off) {
        long i=0;
        i |= (b[off] & 0xffL) <<24;
        i |= (b[off+1] & 0xffL) <<16;
        i |= (b[off+2] & 0xffL) <<8;
        i |= (b[off+3] & 0xffL);
        return i;
    }

    static void longToArray(byte b[], int off, long i) {
        b[off] = (byte)((i>>56) & 0xffL);
        b[off+1] = (byte)((i>>48) & 0xffL);
        b[off+2] = (byte)((i>>40) & 0xffL);
        b[off+3] = (byte)((i>>32) & 0xffL);
        b[off+4] = (byte)((i>>24) & 0xffL);
        b[off+5] = (byte)((i>>16) & 0xffL);
        b[off+6] = (byte)((i>>8) & 0xffL);
        b[off+7] = (byte)(i & 0xffL);
    }

    static long arrayToLong(byte b[], int off) {
        long i=0;
        i |= (b[off] & 0xffL) <<56;
        i |= (b[off+1] & 0xffL) <<48;
        i |= (b[off+2] & 0xffL) <<40;
        i |= (b[off+3] & 0xffL) <<32;
        i |= (b[off+4] & 0xffL) <<24;
        i |= (b[off+5] & 0xffL) <<16;
        i |= (b[off+6] & 0xffL) <<8;
        i |= (b[off+7] & 0xffL);
        return i;
    }

    static boolean contains(byte str1[], byte str2[]) {
        int len1 = str1.length;
        int len2 = str2.length;
        if (len2==0) {
            return true;
        }
        else if (len2>len1) {
            return false;
        }
        boolean found = false;
        int b=0;
        for (int a=0; a<len1; a++) {
            if (str1[a]==str2[b]) {
                b++;
                if (b==len2) {
                    found=true;
                    break;
                }
            }
            else {
                b=0;
            }
        }
        return found;
    }
}
