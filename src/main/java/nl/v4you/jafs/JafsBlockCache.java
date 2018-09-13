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
	static int CACHE_MAX_SIZE = 1000;

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
	
	JafsBlock get(long bpos, JafsBlock block) throws JafsException, IOException {

		CacheEntry ce = null;
		boolean cacheHit = false;
		
		if (bpos<0) {
			// SuperBlock bpos = -1 and is not cached
			throw new JafsException("bpos should be 0 or greater, got: "+bpos);
		}

		if (bpos>=vfs.getSuper().getBlocksTotal()) {
			throw new JafsException("bpos ("+bpos+") >= blocks total ("+vfs.getSuper().getBlocksTotal()+")");
		}
		
		// Check if this block is already in cache
		if (cache.containsKey(bpos)) {
			cacheHit = true;
			ce = cache.get(bpos);
			if (block!=null) {
				throw new JafsException("Cache hit unexpected, block supplied to cache get method");
			}
		}

		if (cacheHit) {
			// Remove our entry from the access list
			CacheEntry l = ce.l;
			CacheEntry r = ce.r;

			if (l != null) l.r = r;
			if (r != null) r.l = l;

			if (ce==mostLeft) mostLeft=mostLeft.r;
			if (ce==mostRight) mostRight=mostRight.l;
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

		// Update access list (add to the right)
		ce.r = null;
		ce.l = mostRight;
		if (mostRight!=null) mostRight.r = ce;
		mostRight = ce;
		if (mostLeft==null) mostLeft=ce;

		if (ce.bpos!=bpos) {
			throw new JafsException("Cached block bpos is not equal to requested bpos");
		}
		return ce.block;
	}
}
