package nl.v4you.jafs;

import java.io.IOException;

class JafsSuper {
	private static final int VERSION = 1;

	private Jafs vfs;
	private JafsBlock rootBlock;
	private int blockSize = 256;
	private int inodeSize = 64;
	private long maxFileSize = 4L*1024L*1024L*1024L;
	private long blocksTotal = 0;
	private long blocksUsed = 0;
	private long rootDirBPos = 1;
	private int rootDirIdx = 0;

	byte buf[] = new byte[64];

	JafsSuper(Jafs vfs, int blockSize) throws JafsException {
		this.vfs = vfs;
		this.blockSize = blockSize;
		rootBlock = new JafsBlock(vfs, -1, blockSize);
	}
	
	long getRootDirBpos() {
		return rootDirBPos;
	}
	
	void setRootDirBpos(long bpos) {
		this.rootDirBPos = bpos;
	}

	int getRootDirIpos() {
		return rootDirIdx;
	}
	
	void setRootDirIpos(int idx) {
		this.rootDirIdx = idx;
	}
	
	long getBlocksTotal() {
		return blocksTotal;
	}

	void incBlocksTotal() {
		blocksTotal++;
	}
	
	void incBlocksTotalAndFlush() throws JafsException, IOException {
		blocksTotal++;
		flush();
	}

	long getBlocksUsed() {
		return blocksUsed;
	}

	void incBlocksUsed() {
		blocksUsed++;
		if (blocksUsed>blocksTotal) {
			throw new RuntimeException("blocksUsed>blocksTotal!!!");
		}
	}

	void incBlocksUsedAndFlush() throws JafsException, IOException {
		incBlocksUsed();
		flush();
	}
	
	void decBlocksUsed() {
		blocksUsed--;
		if (blocksUsed<0) {
			throw new RuntimeException("blocksUsed<0!!!");
		}		
	}
	
	void decBlocksUsedAndFlush() throws JafsException, IOException {
		decBlocksUsed();
		flush();
	}
			
	long getBlocksUnused() {
		return blocksTotal-blocksUsed;
	}

	int getBlockSize() {
		return blockSize;
	}
	
	void setBlockSize(int blockSize) {
		this.blockSize = blockSize;
	}

	int getInodeSize() {
		return inodeSize;
	}
	
	void setInodeSize(int inodeSize) {
		this.inodeSize = inodeSize;
	}
	
	long getMaxFileSize() {
		return maxFileSize;
	}
	
	void setMaxFileSize(long maxFileSize) {
		this.maxFileSize = maxFileSize;
	}
		
	void read() throws JafsException, IOException {
		rootBlock.seekSet(0);
		rootBlock.readFromDisk();
		rootBlock.readBytes(buf, 0, 34);
		blockSize = (int)Util.arrayToInt(buf, 6);
		inodeSize = (int)Util.arrayToInt(buf, 10);
		maxFileSize = Util.arrayToInt(buf, 14);
		rootDirBPos = Util.arrayToInt(buf, 18);
		rootDirIdx = (int)Util.arrayToInt(buf, 22);
		blocksTotal = Util.arrayToInt(buf, 26);
		blocksUsed =  Util.arrayToInt(buf, 30);
	}
	
	void flush() throws JafsException, IOException {
		rootBlock.initZeros();
		rootBlock.seekSet(0);
		buf[0]='J';
		buf[1]='V';
		buf[2]='F';
		buf[3]='S';
		Util.shortToArray(buf, 4, VERSION);
		Util.intToArray(buf,  6, blockSize);
		Util.intToArray(buf, 10, inodeSize);
		Util.intToArray(buf, 14, maxFileSize);
		Util.intToArray(buf, 18, rootDirBPos);
		Util.intToArray(buf, 22, rootDirIdx);
		Util.intToArray(buf, 26, blocksTotal);
		Util.intToArray(buf, 30, blocksUsed);
		rootBlock.writeBytes(buf, 0, 34);
		rootBlock.flushBlock();
	}
}
