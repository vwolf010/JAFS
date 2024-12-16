package nl.v4you.jafs;

import java.util.LinkedList;

class ZDirPool {
    private final LinkedList<ZDir> free = new LinkedList<>();
    private final LinkedList<ZDir> busy = new LinkedList<>();

    private final Jafs vfs;

    ZDirPool(Jafs vfs) {
        this.vfs = vfs;
    }

    ZDir claim() {
        ZDir dir;
        if (free.isEmpty()) {
            dir = new ZDir(vfs);
            busy.add(dir);
        } else {
            dir = free.removeFirst();
            busy.add(dir);
        }
        return dir;
    }

    void release(ZDir dir) {
        busy.remove(dir);
        free.add(dir);
    }

    String stats() {
        return "   free    : " + free.size()+"\n   busy    : " + busy.size()+"\n";
    }
}
