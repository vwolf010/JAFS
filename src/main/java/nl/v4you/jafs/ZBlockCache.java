package nl.v4you.jafs;

import java.io.IOException;
import java.util.HashSet;

class ZBlockCache {
	private final Jafs vfs;
	private final ZLRUCache<Long, ZBlock> gcache;
    private final HashSet<Long> flushList = new HashSet<>(128, 0.5f);

    private ZBlock free = null;

	ZBlockCache(Jafs vfs, int size) {
	    this.vfs = vfs;
	    gcache = new ZLRUCache<>(size);
    }

	ZBlock get(long bpos) throws JafsException, IOException {
        ZBlock blk = gcache.get(bpos);
        if (blk == null) {
            if (free == null) {
                blk = new ZBlock(vfs, bpos);
            } else {
                blk = free;
                blk.setBpos(bpos);
                free = null;
            }
            blk.readFromDisk();
            ZBlock evicted = gcache.add(bpos, blk);
            if (evicted != null) {
                if (evicted.needsFlush()) {
                    evicted.writeToDisk();
                    flushList.remove(evicted.getBpos());
                }
                free = evicted;
            }
        }
		return blk;
	}

    void addToFlushList(long bpos) {
        flushList.add(bpos);
    }

	void flushBlocks() throws JafsException, IOException {
	    for (long bpos : flushList) {
	        if (bpos >= 0) {
                ZBlock block = get(bpos);
                if (block == null) {
                    throw new IllegalStateException("block == null, should not happen");
                }
                if (!block.needsFlush()) {
                    throw new IllegalStateException("!needsFlush, should not happen");
                }
                block.writeToDisk();
            }
        }
        flushList.clear();
    }

	String stats() {
        return gcache.stats();
    }
}
