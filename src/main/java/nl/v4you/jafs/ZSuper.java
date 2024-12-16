package nl.v4you.jafs;

import java.io.IOException;
import java.io.RandomAccessFile;

class ZSuper {
	private static final int VERSION = 1;
	private static final int POS_BLOCK_SIZE = 6;
	private static final int POS_BLOCKS_USED = 10;
	private static final int POS_BLOCKS_TOTAL = 14;
	private static final int POS_UNUSED_STACK_START = 18;
	private static final int HEADER_SIZE = 26;

	private final RandomAccessFile raf;
	private final byte[] buf;
	private int blockSize = 0;
	private long blocksTotal = 0;
	private long blocksUsed = 0;
	private long unusedStackEnd = 0;

	private final Jafs vfs;

	ZSuper(Jafs vfs, int blockSize) throws JafsException, IOException {
		this.vfs = vfs;
		this.raf = vfs.getRaf();
		if (raf.length() == 0) {
			if (blockSize <= 0) {
				throw new JafsException("Unable to create new jafs file with supplied blockSize " + blockSize);
			}
			this.blockSize = blockSize;
			buf = new byte[this.blockSize];
			flush();
		} else {
			readHeader();
			if (this.blockSize > 0 && blockSize > 0 && this.blockSize != blockSize) {
				throw new JafsException("Supplied block size [" + blockSize + "] does not match header block size [" + this.blockSize + "]");
			}
			if (raf.length() < this.blockSize) {
				throw new JafsException("Malformed jafs file, file length (" + raf.length() + ") < block size (" + blockSize + ")");
			}
			buf = new byte[this.blockSize];
		}
	}

	long getBlocksTotal() {
		return blocksTotal;
	}

	long getBlocksUsed() {
		return blocksUsed;
	}

	void setAvailableStackEnd(long unusedStack) {
		this.unusedStackEnd = unusedStack;
	}

	long getAvailableStackEnd() {
		return unusedStackEnd;
	}

	void incBlocksTotal() {
		blocksTotal++;
	}

	void incBlocksUsed() {
		blocksUsed++;
		if (blocksUsed > blocksTotal) {
			throw new IllegalStateException("blocksUsed (" + blocksUsed + ") > blocksTotal (" + blocksTotal + ")");
		}
	}

	void decBlocksUsed() {
		blocksUsed--;
		if (blocksUsed < 0) {
			throw new IllegalStateException("blocksUsed < 0!!!");
		}
	}

	int getBlockSize() {
		return blockSize;
	}

	private void readHeader() throws IOException, JafsException {
		if (raf.length() < HEADER_SIZE) {
			throw new JafsException("File too small, only " + raf.length() + " bytes");
		}
		final byte[] header = new byte[HEADER_SIZE];
		raf.seek(0);
		if (HEADER_SIZE != raf.read(header, 0, HEADER_SIZE)) {
			throw new JafsException("Could not read header");
		}
		if (!(header[0] == 'J' && header[1] == 'A' && header[2] == 'F' && header[3] == 'S')) {
			throw new JafsException("Magic is incorrect");
		}
		int version = ((header[4] & 0xff) << 8) | (header[5] & 0xff);
		if (version != VERSION) {
			throw new JafsException("Version is incorrect, should be " + VERSION + " but got " + version);
		}
		blockSize = (int) ZUtil.arrayToInt(header, POS_BLOCK_SIZE);
		blocksUsed =  ZUtil.arrayToInt(header, POS_BLOCKS_USED);
		blocksTotal = ZUtil.arrayToInt(header, POS_BLOCKS_TOTAL);
		unusedStackEnd = ZUtil.arrayToInt(header, POS_UNUSED_STACK_START);
	}

	private void flush() throws IOException {
		if (raf.length() < 4096) raf.setLength(4096);
		buf[0] = 'J';
		buf[1] = 'A';
		buf[2] = 'F';
		buf[3] = 'S';
		buf[4] = 0;
		buf[5] = VERSION;
		ZUtil.intToArray(buf, POS_BLOCK_SIZE, blockSize);
		ZUtil.intToArray(buf, POS_BLOCKS_USED, blocksUsed);
		ZUtil.intToArray(buf, POS_BLOCKS_TOTAL, blocksTotal);
		ZUtil.intToArray(buf, POS_UNUSED_STACK_START, unusedStackEnd);
		raf.seek(0);
		raf.write(buf, 0, blockSize);
	}

	void popAvailable(long bpos) throws JafsException, IOException {
		ZBlockView block = new ZBlockView(vfs, bpos);
		block.seekSet(0);
		long previousStackEnd = block.readInt();
		setAvailableStackEnd(previousStackEnd);
	}

	void pushAvailable(long bpos) throws JafsException, IOException {
		ZBlockView block = new ZBlockView(vfs, bpos);
		block.seekSet(0);
		block.writeInt(getAvailableStackEnd());
		setAvailableStackEnd(bpos);
	}

	void close() throws IOException {
		flush();
	}
}
