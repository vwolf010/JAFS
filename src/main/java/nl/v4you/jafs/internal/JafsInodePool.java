package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;

import java.util.LinkedList;

public class JafsInodePool {
    private final Jafs vfs;
    private final LinkedList<JafsInode> free = new LinkedList<>();
    private final LinkedList<JafsInode> busy = new LinkedList<>();

    public JafsInodePool(Jafs vfs) {
        this.vfs = vfs;
    }

    public JafsInode claim() {
        JafsInode inode;
        if (free.isEmpty()) {
            inode = new JafsInode(vfs);
            busy.add(inode);
        } else {
            inode = free.removeFirst();
            busy.add(inode);
        }
        return inode;
//        return new JafsInode(vfs);
    }

    public void release(JafsInode inode) {
        busy.remove(inode);
        free.add(inode);
    }

    public String stats() {
        return "   free    : " + free.size()+"\n   busy    : " + busy.size()+"\n";
    }
}
