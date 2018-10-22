package nl.v4you.jafs;

import java.io.*;
import java.util.Arrays;

class JafsBlock {
	private int blockSize = -1;
	private byte[] buf;
	public long bpos = -1;
	private RandomAccessFile raf;
	public int byteIdx = 0;

	int bytesLeft() {
		return blockSize-byteIdx;
	}

	JafsBlock(Jafs vfs, long bpos) {
		this(vfs, bpos, -1);
	}
	
	JafsBlock(Jafs vfs, long bpos, int blockSize) {
		raf = vfs.getRaf();
		if (blockSize<0) {
			blockSize = vfs.getSuper().getBlockSize();
		}
		this.blockSize = blockSize;
		buf = new byte[blockSize];
		this.bpos = bpos;
		byteIdx = 0;
	}

	void initZeros() {
		Arrays.fill(buf, (byte)0);
	}
	
	void seekSet(int b) {
		byteIdx = b;
	}
		
	void readFromDisk() throws IOException {
		long start = (1+bpos) * blockSize;
		raf.seek(start);
		raf.read(buf);
		byteIdx = 0;
	}
	
	void writeToDisk() throws IOException, JafsException {
		long start = (1+bpos) * blockSize;
		long end = start + blockSize;
		if (end>raf.length()) {
			throw new JafsException("Trying to write beyond filesize");
		}
		raf.seek(start);
		raf.write(buf);
	}

//	void dumpBlock(File f) {
//		try {
//			FileOutputStream fos = new FileOutputStream(f);
//			fos.write(buf);
//			fos.close();
//		}
//		catch (Exception e) {
//			System.err.println("Failed to dump block "+f.getAbsolutePath());
//		}
//	}
//
//	void dumpBlock() {
//		File f = new File("c:/data/temp/inode_block_"+bpos+".dmp");
//		dumpBlock(f);
//	}

	int readByte() throws JafsException {
		return buf[byteIdx++] & 0xff;
	}

	void writeByte(int b) throws JafsException {
		buf[byteIdx++] = (byte)(b & 0xff);
	}

	void readBytes(byte b[], int off, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(buf, byteIdx, b, off, len);
		byteIdx += len;
	}

	void writeBytes(byte b[], int off, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(b, off, buf, byteIdx, len);
		byteIdx+=len;
	}
		
	long readInt() throws JafsException {
		long i = 0;
		i |= (buf[byteIdx++] & 0xffL)<<24;
		i |= (buf[byteIdx++] & 0xffL)<<16;
		i |= (buf[byteIdx++] & 0xffL)<< 8;
		i |= (buf[byteIdx++] & 0xffL);
		return i;
	}

	void writeInt(long l) throws JafsException {
		buf[byteIdx++] = (byte)((l >> 24) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 16) & 0xffL);
		buf[byteIdx++] = (byte)((l >>  8) & 0xffL);
		buf[byteIdx++] = (byte)(l & 0xffL);
	}

	long readLong() throws JafsException {
		long l = 0;
		l |= (buf[byteIdx++] & 0xffL)<<56;
		l |= (buf[byteIdx++] & 0xffL)<<48;
		l |= (buf[byteIdx++] & 0xffL)<<40;
		l |= (buf[byteIdx++] & 0xffL)<<32;
		l |= (buf[byteIdx++] & 0xffL)<<24;
		l |= (buf[byteIdx++] & 0xffL)<<16;
		l |= (buf[byteIdx++] & 0xffL)<< 8;
		l |= (buf[byteIdx++] & 0xffL);
		return l;
	}
}
