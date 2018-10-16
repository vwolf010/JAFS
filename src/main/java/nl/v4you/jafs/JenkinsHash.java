package nl.v4you.jafs;

public class JenkinsHash {
    byte b[];

    JenkinsHash(byte b[]) {
        this.b = b;
    }

    JenkinsHash set(byte b[]) {
        this.b = b;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof JenkinsHash)) {
            return false;
        }
        JenkinsHash user = (JenkinsHash) o;
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
        return hash(b);
    }

    static int hash(byte data[]) {
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
