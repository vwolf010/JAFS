package nl.v4you.jafs;

import java.util.LinkedList;

public class JafsDirPool {
    private LinkedList<JafsDir> free = new LinkedList<>();
    private LinkedList<JafsDir> busy = new LinkedList<>();

    private Jafs vfs;

    JafsDirPool(Jafs vfs) {
        this.vfs = vfs;
    }

    JafsDir get() {
        JafsDir dir;
        if (free.size()==0) {
            dir = new JafsDir(vfs);
            busy.add(dir);
        }
        else {
            dir = free.removeFirst();
            busy.add(dir);
        }
        return dir;
    }

    void free(JafsDir dir) {
        free.add(dir);
    }
}
