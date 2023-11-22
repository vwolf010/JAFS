package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;
import nl.v4you.jafs.internal.GenericCache;
import nl.v4you.jafs.internal.JafsBlock;

import java.io.IOException;
import java.util.Set;

public class JafsBlockCache {

	private Jafs vfs;

	private GenericCache<Long, JafsBlock> gcache;

    private JafsBlock free = null;

    public int cacheMaxSize;

	public JafsBlockCache(Jafs vfs, int size) {
	    this.vfs = vfs;
	    cacheMaxSize = size;
	    gcache = new GenericCache<>(size);
    }

	public JafsBlock get(long bpos) throws JafsException, IOException {
		if (bpos < 0) {
			throw new JafsException("bpos should be 0 or greater, got: "+bpos);
		}

		if (bpos >= vfs.getSuper().getBlocksTotal()) {
			throw new JafsException("bpos ("+bpos+") >= blocks total ("+vfs.getSuper().getBlocksTotal()+")");
		}

        JafsBlock blk = gcache.get(bpos);
        if (blk==null) {
            if (free==null) {
                blk = new JafsBlock(vfs, bpos);
            }
            else {
                blk = free;
                free = null;
                blk.setBpos(bpos);
            }
            blk.readFromDisk();
            JafsBlock evicted = gcache.add(bpos, blk);
            if (evicted != null) {
                evicted.writeToDiskIfNeeded();
                free = evicted;
            }
        }

		return blk;
	}

	public void flushBlocks(Set<Long> bl) throws JafsException, IOException {
	    vfs.getSuper().writeToDisk();
	    for (long bpos : bl) {
	        if (bpos >= 0) {
                JafsBlock block = get(bpos);
                block.writeToDiskIfNeeded();
            }
        }
        bl.clear();
    }

	public String stats() {
        return gcache.stats();
    }
}
