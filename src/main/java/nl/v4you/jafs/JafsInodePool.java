package nl.v4you.jafs;

import java.util.LinkedList;

public class JafsInodePool {
    private LinkedList<JafsInode> free = new LinkedList<>();
    private LinkedList<JafsInode> busy = new LinkedList<>();

    private Jafs vfs;

    JafsInodePool(Jafs vfs) {
        this.vfs = vfs;
    }

    JafsInode claim() {
        JafsInode inode;
        if (free.size()==0) {
            inode = new JafsInode(vfs);
            busy.add(inode);
        }
        else {
            inode = free.removeFirst();
            busy.add(inode);
        }
        return inode;
//        return new JafsInode(vfs);
    }

    void release(JafsInode inode) {
        busy.remove(inode);
        free.add(inode);
    }

    String stats() {
        return "   free    : " + free.size()+"\n   busy    : " + busy.size()+"\n";
    }
}
