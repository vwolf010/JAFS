package nl.v4you.jafs;

public class DxHackHash {
    byte b[];

    DxHackHash(byte b[]) {
        this.b = b;
    }

    DxHackHash set(byte b[]) {
        this.b = b;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DxHackHash)) {
            return false;
        }
        DxHackHash user = (DxHackHash) o;
        if (user.b.length!=b.length) {
            return false;
        }
        for (int n=0; n<b.length; n++) {
            if (user.b[n]!=b[n]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return dxHackHash(b);
    }

    static int dxHackHash(byte b[]) {
        long hash0 = 0x12a3fe2dL;
        long hash1 = 0x37abe8f9L;

        for (int n=0; n<b.length; n++) {
            long hash = hash1 + (hash0 ^ ((b[n]&0xff) * 7152373L));
            if ((hash>=0x80000000L)) {
                hash -= 0x7fffffff;
            }
            hash1 = hash0;
            hash0 = hash;
        }
        return (int)hash0;
    }
}
