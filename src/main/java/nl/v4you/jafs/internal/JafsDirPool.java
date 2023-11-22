package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.internal.JafsDir;

import java.util.LinkedList;

public class JafsDirPool {
    private LinkedList<JafsDir> free = new LinkedList<>();
    private LinkedList<JafsDir> busy = new LinkedList<>();

    private Jafs vfs;

    public JafsDirPool(Jafs vfs) {
        this.vfs = vfs;
    }

    public JafsDir claim() {
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

    public void release(JafsDir dir) {
        busy.remove(dir);
        free.add(dir);
    }

    public String stats() {
        return "   free    : " + free.size()+"\n   busy    : " + busy.size()+"\n";
    }
}
