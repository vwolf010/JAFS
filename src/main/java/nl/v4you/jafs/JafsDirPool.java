package nl.v4you.jafs;

import java.util.LinkedList;

public class JafsDirPool {
    private LinkedList<JafsDir> free = new LinkedList<>();
    private LinkedList<JafsDir> busy = new LinkedList<>();

    private Jafs vfs;

    JafsDirPool(Jafs vfs) {
        this.vfs = vfs;
    }

    JafsDir claim() {
        JafsDir dir;
        if (free.isEmpty()) {
            dir = new JafsDir(vfs);
            busy.add(dir);
        }
        else {
            dir = free.removeFirst();
            busy.add(dir);
        }
        return dir;
//        return new JafsDir(vfs);
    }

    void release(JafsDir dir) {
        busy.remove(dir);
        free.add(dir);
    }

    String stats() {
        return "   free    : " + free.size()+"\n   busy    : " + busy.size()+"\n";
    }
}
