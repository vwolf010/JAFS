package nl.v4you.JAFS;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import nl.v4you.JAFS.JAFSBlockCache;
import nl.v4you.JAFS.JAFSFile;
import nl.v4you.JAFS.JAFSInodeContext;
import nl.v4you.JAFS.JAFSSuper;
import nl.v4you.JAFS.JAFSUnusedMap;

public class JAFS {
	private JAFSBlockCache cache;
	private JAFSSuper superBlock;
	private String fname;
	private RandomAccessFile raf;		
	private JAFSInodeContext ctx;
	private JAFSUnusedMap um;
	
	/*
	 * Public
	 */
	public JAFS (String fname) throws IOException, JAFSException {
		init(fname, -1, -1, -1);
	}
	
	public JAFS (String fname, int blockSize, int inodeSize, long maxFileSize) throws JAFSException, IOException {
		if ((blockSize/64)==0) {
			blockSize=64;
		}
		while ((blockSize & 63)>0) {
			blockSize++;			
		}
		if ((inodeSize/32)==0) {
			inodeSize=32;
		}
		while ((inodeSize & 31)>0) {
			inodeSize++;			
		}
		if (maxFileSize<0) {
			maxFileSize = 0;
		}
		if (maxFileSize>JAFSInodeContext.MAX_FILE_SIZE) {
			maxFileSize = JAFSInodeContext.MAX_FILE_SIZE;
		}
		init(fname, blockSize, inodeSize, maxFileSize);
		if (blockSize!=superBlock.getBlockSize()) {
			throw new JAFSException("Supplied block size ["+blockSize+"] does not match header block size ["+superBlock.getBlockSize()+"]");
		}
		if (inodeSize!=superBlock.getInodeSize()) {
			throw new JAFSException("Supplied inode size ["+blockSize+"] does not match header inode size ["+superBlock.getBlockSize()+"]");
		}
		if (maxFileSize!=superBlock.getMaxFileSize()) {
			throw new JAFSException("Supplied max file size ["+blockSize+"] does not match header max file size ["+superBlock.getBlockSize()+"]");
		}
	}
	
	public JAFSFile getFile(String name) throws JAFSException, IOException {
		return new JAFSFile(this, name);
	}
	
	public JAFSFileInputStream getFileInputStream(JAFSFile f) throws JAFSException, IOException {
		return new JAFSFileInputStream(this, f);
	}
	
	public JAFSFileOutputStream getFileOutputStream(JAFSFile f) throws JAFSException, IOException {
		return new JAFSFileOutputStream(this, f);
	}

	public void close() throws IOException {
		raf.close();
	}

	/*
	 * Default
	 */	
	JAFSInodeContext getINodeContext() {
		return ctx;
	}
	
	JAFSUnusedMap getUnusedMap() {
		return um;
	}
	
	long getRootBpos() {
		return superBlock.getRootDirBpos();
	}
	
	int getRootIdx() {
		return superBlock.getRootDirIdx();
	}
	
	RandomAccessFile getRaf() {
		return raf;
	}
		
	JAFSSuper getSuper() {
		return superBlock;
	}

	JAFSBlock setCacheBlock(long bpos, JAFSBlock block) throws JAFSException, IOException {
		return cache.get(bpos, block);
	}
	
	JAFSBlock getCacheBlock(long bpos) throws JAFSException, IOException {
		return cache.get(bpos, null);
	}

	long getNewUnusedBpos() throws JAFSException, IOException {
		long bpos = superBlock.getBlocksTotal();
		if (um.isUnusedMapBlock(bpos)) {
			um.create(bpos);
			bpos = superBlock.getBlocksTotal();
		}		
		superBlock.incBlocksTotalAndFlush();
		raf.setLength(superBlock.getBlockSize()*(superBlock.getBlocksTotal()+1));
		um.setUnusedBlock(bpos);
		return bpos;
	}

	/*
	 * Private
	 */
	private void open(int blockSize, int inodeSize, long maxFileSize) throws IOException, JAFSException {
		File f = new File(fname);
		if (!f.exists() && blockSize<0) {
			throw new JAFSException("["+fname+"] does not exist");
		}
		if (f.exists() && f.length()<64) {
			throw new JAFSException("["+fname+"] does not contain a header");
		}
		raf = new RandomAccessFile(f, "rw");
		if (f.length()==0) {
			raf.setLength(blockSize);
			superBlock = new JAFSSuper(this, blockSize);
			superBlock.setInodeSize(inodeSize);
			superBlock.setMaxFileSize(maxFileSize);
			superBlock.flush();
			cache = new JAFSBlockCache(this);
			um = new JAFSUnusedMap(this);
			ctx = new JAFSInodeContext(this, blockSize, inodeSize, maxFileSize);
			JAFSDir.createRootDir(this);
		}
		else {
			superBlock = new JAFSSuper(this, 64);
			superBlock.read();
			superBlock = new JAFSSuper(this, superBlock.getBlockSize()); // reconnect with correct blocksize
			superBlock.read();
			cache = new JAFSBlockCache(this);
			um = new JAFSUnusedMap(this);
			ctx = new JAFSInodeContext(this, superBlock.getBlockSize(), superBlock.getInodeSize(), superBlock.getMaxFileSize());
		}
	}
	
	private void init(String fname, int blockSize, int inodeSize, long maxFileSize) throws JAFSException, IOException {
		this.fname = fname;
		open(blockSize, inodeSize, maxFileSize);
	}
}
