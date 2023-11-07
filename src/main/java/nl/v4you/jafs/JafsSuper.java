package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

class JafsSuper {
	private static final int VERSION = 1;

	private Jafs vfs;
	private JafsBlock rootBlock;
	private int blockSize;
	private long maxFileSize = 4L * 1024L * 1024L * 1024L;
	private long blocksTotal = 0;
	private long blocksUsed = 0;
	private long rootDirBPos = 1;
	byte[] buf = new byte[64];

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
		rootBlock.seekSet(14);
		rootBlock.writeInt(blockList, bpos);
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
		rootBlock.seekSet(18);
		rootBlock.writeInt(blockList, blocksTotal);
	}

	void writeBlocksUsed(Set<Long> blockList) {
		rootBlock.seekSet(22);
		rootBlock.writeInt(blockList, blocksUsed);
	}

	void incBlocksUsed(Set<Long> blockList) {
		blocksUsed++;
		if (blocksUsed > blocksTotal) {
			throw new RuntimeException("blocksUsed ("+blocksUsed+") > blocksTotal ("+blocksTotal+")");
		}
		writeBlocksUsed(blockList);
	}

	void decBlocksUsed(Set<Long> blockList) {
		blocksUsed--;
		if (blocksUsed < 0) {
			throw new RuntimeException("blocksUsed < 0!!!");
		}
		writeBlocksUsed(blockList);
	}

	int getBlockSize() {
		return blockSize;
	}

	void setBlockSize(Set<Long> blockList, int blockSize) {
		rootBlock.seekSet(6);
		rootBlock.writeInt(blockList, blockSize);
	}

	long getMaxFileSize() {
		return maxFileSize;
	}
	
	void setMaxFileSize(Set<Long> blockList, long maxFileSize) {
		this.maxFileSize = maxFileSize;
		rootBlock.seekSet(10);
		rootBlock.writeInt(blockList, maxFileSize);
	}

	void read() throws IOException {
		rootBlock.seekSet(0);
		rootBlock.readFromDisk();
		rootBlock.readBytes(buf, 0, 26);
		blockSize = (int)Util.arrayToInt(buf, 6);
		maxFileSize = Util.arrayToInt(buf, 10);
		rootDirBPos = Util.arrayToInt(buf, 14);
		blocksTotal = Util.arrayToInt(buf, 18);
		blocksUsed =  Util.arrayToInt(buf, 22);
	}

	void writeToDisk() throws IOException, JafsException {
		rootBlock.writeToDiskIfNeeded();
	}
	
	void setRafSize() throws IOException {
		vfs.getRaf().setLength((1+blocksTotal)*blockSize);
	}
}
