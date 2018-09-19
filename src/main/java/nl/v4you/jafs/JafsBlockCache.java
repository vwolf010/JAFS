package nl.v4you.jafs;

import java.io.IOException;

class JafsBlockCache {

	private Jafs vfs;

	private GenericCache<Long, JafsBlock> gcache;

	public int cacheMaxSize = 100;

	JafsBlockCache(Jafs vfs, int size) throws JafsException {
	    this.vfs = vfs;
	    cacheMaxSize = size;
	    gcache = new GenericCache<>(size);
    }

	JafsBlock get(long bpos, JafsBlock block) throws JafsException, IOException {
		if (bpos<0) {
			// SuperBlock bpos = -1 and is not cached
			throw new JafsException("bpos should be 0 or greater, got: "+bpos);
		}

		if (bpos>=vfs.getSuper().getBlocksTotal()) {
			throw new JafsException("bpos ("+bpos+") >= blocks total ("+vfs.getSuper().getBlocksTotal()+")");
		}

        JafsBlock blk = gcache.get(bpos);
		if (block==null) {
		    if (blk==null) {
                blk = new JafsBlock(vfs, bpos);
                blk.readFromDisk();
                gcache.add(bpos, blk);
            }
        }
        else {
            if (blk!=null) {
                throw new JafsException("Cache hit unexpected, block supplied to cache get method");
            }
            blk = block;
            gcache.add(bpos, blk);
        }

		return blk;
	}
}
