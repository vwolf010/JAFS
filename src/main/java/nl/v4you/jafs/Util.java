package nl.v4you.jafs;

public class Util {

    static void shortToArray(byte b[], int i) {
        b[0] = (byte)((i>>8) & 0xff);
        b[1] = (byte)(i & 0xff);
    }

    static int arrayToShort(byte b[]) {
        int i=0;
        i |= (b[0] & 0xff) <<8;
        i |= (b[1] & 0xff);
        return i;
    }

    static void intToArray(byte b[], int i) {
        b[0] = (byte)((i>>24) & 0xff);
        b[1] = (byte)((i>>16) & 0xff);
        b[2] = (byte)((i>>8) & 0xff);
        b[3] = (byte)(i & 0xff);
    }

    static int arrayToInt(byte b[]) {
        int i=0;
        i |= (b[0] & 0xff) <<24;
        i |= (b[1] & 0xff) <<16;
        i |= (b[2] & 0xff) <<8;
        i |= (b[3] & 0xff);
        return i;
    }
}
