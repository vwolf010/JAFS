package nl.v4you.jafs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Set;
import java.util.TreeSet;

// https://www.linuxjournal.com/article/2151

public class Jafs {

    private static int CACHE_BLOCK_MAX = 100*1000;
    private static int CACHE_DIR_MAX   = 1000*1000;

	private JafsBlockCache cache;
	private JafsDirEntryCache dirCache;
	private JafsSuper superBlock;
	private String fname;
	private RandomAccessFile raf;		
	private JafsInodeContext ctx;
	private JafsUnusedMapEqual um;
	private JafsDirEntry rootEntry = null;
	private JafsInodePool inodePool = null;
	private JafsDirPool dirPool = null;

	/*
	 * Public
	 */
	public Jafs(String fname) throws IOException, JafsException {
		init(fname, -1, -1);
	}

	boolean isSupportedSize(int size, int sizeMin, int sizeMax) {
        boolean supported = false;
        int n;
        for (n=1; n<=16; n++) {
            int pow = 2 << n;
            if (size==pow && sizeMin<=size && size<=sizeMax) {
                supported = true;
                break;
            }
        }
        return supported;
    }
	
	public Jafs(String fname, int blockSize, long maxFileSize) throws JafsException, IOException {
		if (!isSupportedSize(blockSize, 64, 8192)) {
			throw new JafsException("block size "+blockSize+" not supported");
		}
		if (maxFileSize<0) {
			maxFileSize = 0;
		}
		if (maxFileSize>JafsInodeContext.MAX_FILE_SIZE) {
			maxFileSize = JafsInodeContext.MAX_FILE_SIZE;
		}
		init(fname, blockSize, maxFileSize);
		if (blockSize!=superBlock.getBlockSize()) {
			throw new JafsException("Supplied block size ["+blockSize+"] does not match header block size ["+superBlock.getBlockSize()+"]");
		}
		if (maxFileSize!=superBlock.getMaxFileSize()) {
			throw new JafsException("Supplied max file size ["+maxFileSize+"] does not match header max file size ["+superBlock.getMaxFileSize()+"]");
		}
	}
	
	public JafsFile getFile(String name) throws JafsException {
		return new JafsFile(this, name);
	}
	
	public JafsFile getFile(String parent, String name) throws JafsException {
		return new JafsFile(this, parent, name);
	}

	public JafsFile getFile(JafsFile parent, String name) throws JafsException {
		return new JafsFile(this, parent, name);
	}

	public JafsInputStream getInputStream(JafsFile f) throws JafsException, IOException {
		return new JafsInputStream(this, f);
	}
	
	public JafsOutputStream getOutputStream(JafsFile f) throws JafsException, IOException {
		return getOutputStream(f, false);
	}

	public JafsOutputStream getOutputStream(JafsFile f, boolean append) throws JafsException, IOException {
		if (!append && f.exists() && !f.delete()) {
			throw new JafsException("getOutputStream(): deleting ["+f.getAbsolutePath()+"] failed");
		}
		JafsOutputStream jos = new JafsOutputStream(this, f, append);

		Set<Long> blockList = new TreeSet();
		cache.flushBlocks(blockList);
		return jos;
	}

	public void close() throws IOException {
		raf.close();
	}

	/*
	 * Package private
	 */
	JafsInodeContext getINodeContext() {
		return ctx;
	}
	
	JafsUnusedMapEqual getUnusedMap() {
		return um;
	}
	
	long getRootBpos() {
		return superBlock.getRootDirBpos();
	}
	
	int getRootIpos() {
		return superBlock.getRootDirIpos();
	}
	
	RandomAccessFile getRaf() {
		return raf;
	}
		
	JafsSuper getSuper() {
		return superBlock;
	}

	JafsBlock getCacheBlock(long bpos) throws JafsException, IOException {
		return cache.get(bpos);
	}

	JafsBlockCache getBlockCache() {
	    return cache;
    }

    JafsDirEntryCache getDirCache() {
        return dirCache;
    }

    long appendNewBlockToArchive(Set<Long> blockList) throws JafsException, IOException {
		long bpos = superBlock.getBlocksTotal();
		if (bpos==um.getUnusedMapBpos(bpos)) {
			um.createNewUnusedMap(blockList, bpos);
			bpos = superBlock.getBlocksTotal();
		}
		superBlock.incBlocksTotal(blockList);
		return bpos;
	}

    JafsDirEntry getRootEntry() {
        if (rootEntry != null) {
            return rootEntry;
        } else {
            rootEntry = new JafsDirEntry();
            rootEntry.parentBpos = getRootBpos();
            rootEntry.parentIpos = getRootIpos();
            rootEntry.bpos = getRootBpos();
            rootEntry.ipos = getRootIpos();
            rootEntry.type = JafsInode.INODE_DIR;
            rootEntry.name = "/".getBytes();
            return rootEntry;
        }
    }

    /*
	 * Private
	 */
    private void initInodeContext(int blockSize, long maxFileSize) {
		um = new JafsUnusedMapEqual(this);
		ctx = new JafsInodeContext(this, blockSize, maxFileSize);
	}


    private void open(int blockSize, long maxFileSize) throws IOException, JafsException {
		File f = new File(fname);
		if (!f.exists() && blockSize<0) {
			throw new JafsException("["+fname+"] does not exist");
		}
		if (f.exists() && f.length()<64) {
			throw new JafsException("["+fname+"] does not contain a header");
		}
		raf = new RandomAccessFile(f, "rw");
		cache = new JafsBlockCache(this, CACHE_BLOCK_MAX);
		dirCache = new JafsDirEntryCache(CACHE_DIR_MAX);
		inodePool = new JafsInodePool(this);
		dirPool = new JafsDirPool(this);
		if (f.length()==0) {
			raf.setLength(blockSize);
			superBlock = new JafsSuper(this, blockSize);
			Set<Long> blockList = new TreeSet<>();
			superBlock.setMaxFileSize(blockList, maxFileSize);
			initInodeContext(blockSize, maxFileSize);
			JafsDir.createRootDir(blockList, this);
			cache.flushBlocks(blockList);
		}
		else {
			superBlock = new JafsSuper(this, 64);
			superBlock.read();
			superBlock = new JafsSuper(this, superBlock.getBlockSize()); // reconnect with correct blocksize
			superBlock.read();
			initInodeContext(superBlock.getBlockSize(), superBlock.getMaxFileSize());
		}
	}
	
	private void init(String fname, int blockSize, long maxFileSize) throws JafsException, IOException {
		this.fname = fname;
		open(blockSize, maxFileSize);
	}

	JafsInodePool getInodePool() {
        return inodePool;
    }

    JafsDirPool getDirPool() {
    	return dirPool;
	}

	public String stats() {
        StringBuilder sb = new StringBuilder();
        sb.append("blocksUsed         : "+superBlock.getBlocksUsed()+"\n");
        sb.append("blocksTotal        : "+superBlock.getBlocksTotal()+"\n\n");
        sb.append(ctx.toString()+"\n");
        sb.append("blockCache:\n"+cache.stats());
        sb.append("inodePool:\n"+inodePool.stats());
        sb.append("dirPool:\n"+dirPool.stats());
        sb.append("dirCache:\n"+dirCache.stats());
        return sb.toString();
    }

    private void adviceBlockSizeScan(Fsize fsize, JafsFile f) throws JafsException, IOException {
        JafsFile l[] = f.listFiles();
        for (JafsFile g : l) {
            if (g.isFile()) {
                long size = g.length();
                for (int n=0; n<fsize.sizes.length; n++) {
                    long bs = fsize.sizes[n];
                    long mod = (size % bs);
                    if (mod == 0) {
                        fsize.onDisk[n] += (size / bs) * bs;
                    } else {
                        fsize.onDisk[n] += (size / bs) * bs + bs;
                    }
                    fsize.lost[n] += bs - mod;
                }
            }
            else {
                adviceBlockSizeScan(fsize, g);
            }
        }
    }

    public void adviceBlockSize() throws JafsException, IOException {
        Fsize fsize = new Fsize();
        JafsFile f = getFile("/");
        adviceBlockSizeScan(fsize, f);
        for (int n=0; n<fsize.sizes.length; n++) {
            System.err.println("blockSize " + fsize.sizes[n]+": " + fsize.onDisk[n] + " (on disk), " + fsize.lost[n] + " (lost), " + ((fsize.lost[n] * 100.f) / fsize.onDisk[n]) + "%");
        }
    }

    class Fsize {
        long sizes[] = {64, 128, 256, 512, 1024, 2048, 4096};
        long onDisk[] = new long[sizes.length];
        long lost[] = new long[sizes.length];
    }
}
