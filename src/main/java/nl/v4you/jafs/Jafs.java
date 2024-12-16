package nl.v4you.jafs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

// https://www.linuxjournal.com/article/2151

public class Jafs implements AutoCloseable {

	private static final int CACHE_BLOCK_MAX = 16 * 1024;
	private static final int CACHE_DIR_MAX   = 256 * 256;

	private File myFile;
	private RandomAccessFile raf;
	private ZBlockCache blockCache;
	private ZDirEntryCache dirCache;
	private ZSuper superBlock;
	private ZInodeContext ctx;
	private ZDirEntry rootEntry = null;
	private ZFilePool zFilePool = null;
	private ZDirPool dirPool = null;

	boolean isSupportedSize(int size, int sizeMin, int sizeMax) {
		boolean supported = false;
		int n;
		for (n = 1; n <= 16; n++) {
			int pow = 2 << n;
			if (size == pow && sizeMin <= size && size <= sizeMax) {
				supported = true;
				break;
			}
		}
		return supported;
	}

	public Jafs(String fname) throws IOException, JafsException {
		myFile = new File(fname);
		init(fname, 0);
	}

	public Jafs(String fname, int blockSize) throws JafsException, IOException {
		if (!isSupportedSize(blockSize, 64, 4096)) {
			throw new JafsException("block size " + blockSize + " not supported");
		}
		init(fname, blockSize);
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
		if (f.exists()) {
			if (f.isDirectory()) {
				throw new JafsException(f.getCanonicalPath() + " should not be a directory");
			}
		}
		return new JafsOutputStream(this, f, append);
	}

	public void close() throws IOException {
		if (superBlock != null) {
			try {
				superBlock.close();
			}
			catch (Exception e) {
				throw new IOException("error when closing super block");
			}
		}
		raf.close();
	}

	ZInodeContext getINodeContext() {
		return ctx;
	}

	RandomAccessFile getRaf() {
		return raf;
	}

	ZSuper getSuper() {
		return superBlock;
	}

	ZBlockCache getBlockCache() {
		return blockCache;
	}

	void flushBlockCache() throws JafsException, IOException {
		blockCache.flushBlocks();
	}

	ZDirEntryCache getDirCache() {
		return dirCache;
	}

	long getAvailableVpos() throws JafsException, IOException {
		long bpos = superBlock.getAvailableStackEnd();
		if (bpos != 0) {
			superBlock.popAvailable(bpos);
		} else {
			superBlock.incBlocksTotal();
			bpos = superBlock.getBlocksTotal();
		}
		getSuper().incBlocksUsed();
		return bpos;
	}

	ZDirEntry getRootEntry() {
		if (rootEntry != null) {
			return rootEntry;
		} else {
			rootEntry = new ZDirEntry();
			rootEntry.parentBpos = 1;
			rootEntry.vpos = 1;
			rootEntry.type = ZFile.INODE_DIR;
			rootEntry.setName(JafsFile.SEPARATOR.getBytes());
			return rootEntry;
		}
	}

	private void initInodeContext(int blockSize) {
		ctx = new ZInodeContext(this, blockSize);
	}

	private void open(int blockSize) throws IOException, JafsException {
		if (!myFile.exists() && blockSize < 0) {
			throw new JafsException("[" + myFile.getName() + "] does not exist");
		}
		raf = new RandomAccessFile(myFile, "rw");
		blockCache = new ZBlockCache(this, CACHE_BLOCK_MAX);
		dirCache = new ZDirEntryCache(CACHE_DIR_MAX);
		zFilePool = new ZFilePool(this);
		dirPool = new ZDirPool(this);
		boolean isNewFile = myFile.length() == 0;
		superBlock = new ZSuper(this, blockSize);
		initInodeContext(superBlock.getBlockSize());
		if (isNewFile) {
			ZDir.createRootDir(this);
			blockCache.flushBlocks();
		}
	}

	private void init(String fname, int blockSize) throws JafsException, IOException {
		myFile = new File(fname);
		open(blockSize);
	}

	ZFilePool getZFilePool() {
		return zFilePool;
	}

	ZDirPool getDirPool() {
		return dirPool;
	}

	long getBlocksUsed() {
		return superBlock.getBlocksUsed();
	}

	long getBlocksTotal() {
		return superBlock.getBlocksTotal();
	}

	public String stats() {
        return "blocksUsed         : " + superBlock.getBlocksUsed() + "\n" +
                "blocksTotal        : " + superBlock.getBlocksTotal() + "\n\n" +
                ctx.toString() + "\n" +
                "blockCache:\n" + blockCache.stats() +
                "inodePool:\n" + zFilePool.stats() +
                "dirPool:\n" + dirPool.stats() +
                "dirCache:\n" + dirCache.stats();
	}
}