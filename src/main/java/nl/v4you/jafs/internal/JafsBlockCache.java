package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.util.TreeSet;

public class JafsBlockCache {
	private Jafs vfs;
	private LRUCache<Long, JafsBlock> gcache;
    private JafsBlock free = null;
    public int cacheMaxSize;

    private TreeSet<Long> flushList = new TreeSet<>();

	public JafsBlockCache(Jafs vfs, int size) {
	    this.vfs = vfs;
	    cacheMaxSize = size;
	    gcache = new LRUCache<>(size);
    }

	public JafsBlock get(long bpos) throws JafsException, IOException {
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
                flushList.remove(evicted.bpos);
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
                block.writeToDiskIfNeeded();
            }
        }
        flushList.clear();
    }

	public String stats() {
        return gcache.stats();
    }
}
