package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.util.Set;

public class JafsBlockCache {
	private Jafs vfs;
	private LRUCache<Long, JafsBlock> gcache;
    private JafsBlock free = null;
    public int cacheMaxSize;

	public JafsBlockCache(Jafs vfs, int size) {
	    this.vfs = vfs;
	    cacheMaxSize = size;
	    gcache = new LRUCache<>(size);
    }

	public JafsBlock get(Set<Long> bl, long bpos) throws JafsException, IOException {
		if (bpos < 0) {
			throw new JafsException("bpos should be 0 or greater, got: "+bpos);
		}

		if (bpos >= vfs.getSuper().getBlocksTotal()) {
			throw new JafsException("bpos ("+bpos+") >= blocks total ("+vfs.getSuper().getBlocksTotal()+")");
		}

        JafsBlock blk = gcache.get(bpos);
        if (blk == null) {
            if (free == null) {
                blk = new JafsBlock(vfs, bpos);
            }
            else {
                blk = free;
                blk.setBpos(bpos);
                free = null;
            }
            blk.readFromDisk();
            JafsBlock evicted = gcache.add(bpos, blk);
            if (evicted != null) {
                evicted.writeToDiskIfNeeded();
                bl.remove(evicted.bpos);
                free = evicted;
            }
        }

		return blk;
	}

	public void flushBlocks(Set<Long> bl) throws JafsException, IOException {
	    for (long bpos : bl) {
	        if (bpos >= 0) {
                JafsBlock block = get(bl, bpos);
                block.writeToDiskIfNeeded();
            }
        }
        bl.clear();
    }

	public String stats() {
        return gcache.stats();
    }
}
