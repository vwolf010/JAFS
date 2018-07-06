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
	private static int CACHE_MAX_SIZE = 1000;

	private Jafs vfs;
	private Map<Long, CacheEntry> cache = new HashMap<Long, CacheEntry>();
	private CacheEntry mostLeft = null;
	private CacheEntry mostRight = null;
	
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
			throw new JafsException("bpos should be 0 or greater");
		}

		if (bpos>=vfs.getSuper().getBlocksTotal()) {
			throw new JafsException("bpos >= blocks total");
		}
		
		/*
		 * Check if this block is already in cache
		 */
		if (cache.containsKey(bpos)) {
			cacheHit = true;
			ce = cache.get(bpos);
			if (block!=null) {
				throw new JafsException("Cache hit unexpected, block supplied to cache get method");
			}
		}
		
		if (cacheHit) {
			CacheEntry l;
			CacheEntry r;
			
			// Re-insert query at the end of the access list:
			
			// 1) Remove our entry from the access list
			l = ce.l;
			r = ce.r;			
			if (l!=null) l.r = r;
			if (r!=null) r.l = l;
			
			// 2) Add it to the end of the access list
			ce.l = mostRight;
			ce.r = null;
			mostRight = ce;
		}
		else {
			/*
			 * If not in cache, add it to the cache
			 */
			if (cache.size()>=CACHE_MAX_SIZE) {
				/*
				 * Cache too big? Evict (=delete) the oldest entry
				 */
				CacheEntry tmp = mostLeft;
				mostLeft = mostLeft.r;
				cache.remove(tmp.bpos);
			}
			
			/*
			 * Create new entry
			 */
			ce = new CacheEntry();
			ce.bpos = bpos;
			if (block!=null) {
				ce.block = block;
			}
			else {
				ce.block = new JafsBlock(vfs, bpos);
				ce.block.readFromDisk();
			}
			
			// Update access list
			if (mostLeft==null) {
				mostLeft=ce;
			}
			ce.r = null;
			ce.l = mostRight;
			mostRight = ce;
			
			cache.put(bpos, ce);
		}
		if (ce.bpos!=bpos) {
			throw new JafsException("Cached block bpos is not equal to requested bpos");
		}
		return ce.block;
	}
}
