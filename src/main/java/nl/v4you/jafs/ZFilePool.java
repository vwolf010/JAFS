package nl.v4you.jafs;

import java.util.LinkedList;

class ZFilePool {
    private final Jafs vfs;
    private final LinkedList<ZFile> free = new LinkedList<>();
    private final LinkedList<ZFile> busy = new LinkedList<>();

    ZFilePool(Jafs vfs) {
        this.vfs = vfs;
    }

    ZFile claim() {
        ZFile inode;
        if (free.isEmpty()) {
            inode = new ZFile(vfs);
            busy.add(inode);
        } else {
            inode = free.removeFirst();
            busy.add(inode);
        }
        return inode;
    }

    void release(ZFile inode) {
        busy.remove(inode);
        free.add(inode);
    }

    String stats() {
        return "   free    : " + free.size()+"\n   busy    : " + busy.size()+"\n";
    }
}
