package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

class JafsSuper {
	private static final int VERSION = 1;

	private Jafs vfs;
	private JafsBlock rootBlock;
	private int blockSize;
	private int inodeSize = 64;
	private long maxFileSize = 4L*1024L*1024L*1024L;
	private long blocksTotal = 0;
	private long blocksUsed = 0;
	private long rootDirBPos = 1;
	private int rootDirIdx = 0;

	byte buf[] = new byte[64];

	JafsSuper(Jafs vfs, int blockSize) {
		this.vfs = vfs;
		this.blockSize = blockSize;
		rootBlock = new JafsBlock(vfs, -1, blockSize);
		Set<Long> blockList = new TreeSet<>();
		rootBlock.writeBytes(blockList, "JAFS".getBytes());
		rootBlock.writeByte(blockList, 0);
		rootBlock.writeByte(blockList, VERSION);
		setBlockSize(blockList, blockSize);
	}
	
	long getRootDirBpos() {
		return rootDirBPos;
	}
	
	void setRootDirBpos(Set<Long> blockList, long bpos) {
		this.rootDirBPos = bpos;
		rootBlock.seekSet(18);
		rootBlock.writeInt(blockList, bpos);
	}

	int getRootDirIpos() {
		return rootDirIdx;
	}
	
	void setRootDirIpos(Set<Long> blockList, int iPos) {
		this.rootDirIdx = iPos;
		rootBlock.seekSet(22);
		rootBlock.writeInt(blockList, iPos);
	}
	
	long getBlocksTotal() {
		return blocksTotal;
	}

	long getBlocksUsed() {
		return blocksUsed;
	}

	void incBlocksTotalAndUsed(Set<Long> blockList) {
		incBlocksTotal(blockList);
		incBlocksUsed(blockList);
	}
	
	void incBlocksTotal(Set<Long> blockList) {
		blocksTotal++;
		rootBlock.seekSet(26);
		rootBlock.writeInt(blockList, blocksTotal);
	}

	void writeBlocksUsed(Set<Long> blockList) {
		rootBlock.seekSet(30);
		rootBlock.writeInt(blockList, blocksUsed);
	}

	void incBlocksUsed(Set<Long> blockList) {
		blocksUsed++;
		if (blocksUsed>blocksTotal) {
			throw new RuntimeException("blocksUsed ("+blocksUsed+") > blocksTotal ("+blocksTotal+")");
		}
		writeBlocksUsed(blockList);
	}

	void decBlocksUsed(Set<Long> blockList) {
		blocksUsed--;
		if (blocksUsed<0) {
			throw new RuntimeException("blocksUsed<0!!!");
		}
		writeBlocksUsed(blockList);
	}

	void decBlocksUsedAndFlush(Set<Long> blockList) {
		decBlocksUsed(blockList);
	}
			
	int getBlockSize() {
		return blockSize;
	}

	void setBlockSize(Set<Long> blockList, int blockSize) {
		rootBlock.seekSet(6);
		rootBlock.writeInt(blockList, blockSize);
	}

	int getInodeSize() {
		return inodeSize;
	}
	
	void setInodeSize(Set<Long> blockList, int inodeSize) {
		rootBlock.seekSet(10);
		rootBlock.writeInt(blockList, inodeSize);
		this.inodeSize = inodeSize;
	}
	
	long getMaxFileSize() {
		return maxFileSize;
	}
	
	void setMaxFileSize(Set<Long> blockList, long maxFileSize) {
		this.maxFileSize = maxFileSize;
		rootBlock.seekSet(14);
		rootBlock.writeInt(blockList, maxFileSize);
	}

	void read() throws IOException {
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

	void writeToDisk() throws IOException, JafsException {
		rootBlock.writeToDiskIfNeeded();
	}
	
	void setRafSize() throws IOException {
		vfs.getRaf().setLength((1+blocksTotal)*blockSize);
	}
}
