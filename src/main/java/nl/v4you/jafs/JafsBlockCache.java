package nl.v4you.jafs;

import java.io.IOException;

class JafsBlockCache {

	private Jafs vfs;

	private GenericCache<Long, JafsBlock> gcache;

    private JafsBlock free = null;

    public int cacheMaxSize = 100;

	JafsBlockCache(Jafs vfs, int size) throws JafsException {
	    this.vfs = vfs;
	    cacheMaxSize = size;
	    gcache = new GenericCache<>(size);
    }

	JafsBlock get(long bpos) throws JafsException, IOException {
		if (bpos<0) {
			// SuperBlock bpos = -1 and is not cached
			throw new JafsException("bpos should be 0 or greater, got: "+bpos);
		}

		if (bpos>=vfs.getSuper().getBlocksTotal()) {
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
            if (evicted!=null) {
                free=evicted;
            }
        }

		return blk;
	}

	String stats() {
        return gcache.stats();
    }
}
