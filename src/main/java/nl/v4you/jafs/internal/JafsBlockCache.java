package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.util.TreeSet;

public class JafsBlockCache {
	private final Jafs vfs;
	private final LRUCache<Long, JafsBlock> gcache;
    private final TreeSet<Long> flushList = new TreeSet<>();

    private JafsBlock free = null;

	public JafsBlockCache(Jafs vfs, int size) {
	    this.vfs = vfs;
	    gcache = new LRUCache<>(size);
    }

	public JafsBlock get(long bpos) throws JafsException, IOException {
        JafsBlock blk = gcache.get(bpos);
        if (blk == null) {
            if (free == null) {
                blk = new JafsBlock(vfs, bpos);
            } else {
                blk = free;
                blk.setBpos(bpos);
                free = null;
            }
            blk.readFromDisk();
            JafsBlock evicted = gcache.add(bpos, blk);
            if (evicted != null) {
                if (evicted.needsFlush) {
                    evicted.writeToDisk();
                    flushList.remove(evicted.bpos);
                }
                free = evicted;
            }
        }
		return blk;
	}

    public void addToFlushList(long bpos) {
        flushList.add(bpos);
    }

	public void flushBlocks() throws JafsException, IOException {
	    for (long bpos : flushList) {
	        if (bpos >= 0) {
                JafsBlock block = get(bpos);
                if (block == null || !block.needsFlush) {
                    throw new IllegalStateException("should not happen");
                }
                block.writeToDisk();
            }
        }
        flushList.clear();
    }

	public String stats() {
        return gcache.stats();
    }
}
