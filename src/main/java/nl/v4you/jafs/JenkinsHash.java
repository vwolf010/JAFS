package nl.v4you.jafs;

import java.util.Arrays;

public class JenkinsHash {
    byte b[];
    int hash; // defaults to 0

    JenkinsHash(byte b[]) {
        this.b = b;
    }

    JenkinsHash set(byte b[]) {
        this.b = b;
        hash=0;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        return Arrays.equals(b, ((JenkinsHash) o).b);
    }

    @Override
    public int hashCode() {
        if (hash==0 && b.length>0) {
            hash = calcHash(b);
        }
        return hash;
    }

    static int calcHash(byte data[]) {
        int hash = 0;

        for (byte b : data) {
            hash += (b & 0xff);
            hash += (hash << 10);
            hash ^= (hash >>> 6);
        }

        hash += (hash << 3);
        hash ^= (hash >>> 11);
        hash += (hash << 15);
        return hash;
    }
}
