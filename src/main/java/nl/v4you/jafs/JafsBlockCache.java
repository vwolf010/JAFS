package nl.v4you.jafs;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class CacheEntry {
	long bpos;
	JafsBlock block;
	CacheEntry l;
	CacheEntry r;
}

class JafsBlockCache {
	static int CACHE_MAX_SIZE = 1000; // 3 is the minimum size!

	private Jafs vfs;
	private Map<Long, CacheEntry> cache = new HashMap<>();
	private CacheEntry mostLeft;
	private CacheEntry mostRight;

	JafsBlockCache(Jafs vfs) {
		this.vfs = vfs;
		mostLeft = null;
		mostRight = null;
		cache.clear();
	}

	boolean isInCache(long bpos) {
        return cache.containsKey(bpos);
	}

	JafsBlock get(long bpos, JafsBlock block) throws JafsException, IOException {
		CacheEntry ce = null;

		if (bpos<0) {
			// SuperBlock bpos = -1 and is not cached
			throw new JafsException("bpos should be 0 or greater, got: "+bpos);
		}

		if (bpos>=vfs.getSuper().getBlocksTotal()) {
			throw new JafsException("bpos ("+bpos+") >= blocks total ("+vfs.getSuper().getBlocksTotal()+")");
		}

		// Check if this block is already in cache
		if (cache.containsKey(bpos)) {
			ce = cache.get(bpos);
			if (block!=null) {
				throw new JafsException("Cache hit unexpected, block supplied to cache get method");
			}

			// Remove this entry from the access list
            if (ce==mostLeft) mostLeft=ce.r;
            if (mostLeft!=null) mostLeft.l=null;

            if (ce==mostRight) mostRight=ce.l;
            if (mostRight!=null) mostRight.r=null;

			if (ce.l != null) ce.l.r = ce.r;
			if (ce.r != null) ce.r.l = ce.l;
		}

		if (cache.size()>=CACHE_MAX_SIZE) {
			 // Cache too big? Evict (=delete) the oldest entry
			CacheEntry tmp = mostLeft;
			mostLeft = mostLeft.r;
			mostLeft.l = null;
			cache.remove(tmp.bpos);
		}

		if (ce==null) {
			 // Create new entry
			ce = new CacheEntry();
			ce.bpos = bpos;
			if (block != null) {
				ce.block = block;
			} else {
				ce.block = new JafsBlock(vfs, bpos);
				ce.block.readFromDisk();
			}
			cache.put(bpos, ce);
		}

		// First entry? Set mostleft
        if (mostLeft==null) mostLeft=ce;

        // Update access list (add to the right)
		ce.r = null;
		ce.l = mostRight;
		if (mostRight!=null) mostRight.r = ce;
		mostRight = ce;

		if (ce.bpos!=bpos) {
			throw new JafsException("Cached block bpos is not equal to requested bpos");
		}
		return ce.block;
	}
}
