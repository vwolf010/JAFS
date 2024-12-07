package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;

import java.util.LinkedList;

public class JafsDirPool {
    private final LinkedList<JafsDir> free = new LinkedList<>();
    private final LinkedList<JafsDir> busy = new LinkedList<>();

    private final Jafs vfs;

    public JafsDirPool(Jafs vfs) {
        this.vfs = vfs;
    }

    public JafsDir claim() {
        JafsDir dir;
        if (free.isEmpty()) {
            dir = new JafsDir(vfs);
            busy.add(dir);
        } else {
            dir = free.removeFirst();
            busy.add(dir);
        }
        return dir;
    }

    public void release(JafsDir dir) {
        busy.remove(dir);
        free.add(dir);
    }

    public String stats() {
        return "   free    : " + free.size()+"\n   busy    : " + busy.size()+"\n";
    }
}
