package nl.v4you.JAFS;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

class JAFSBlock {
//	private static final int MAX_BLOCKS = 10000;
	
	private int blockSize = -1;
	private byte[] buf;
	public long bpos = -1;
	private RandomAccessFile raf;
	public int byteIdx = 0;

	private void init(JAFS vfs) {
		raf = vfs.getRaf();
		if (blockSize<0) {
			blockSize = vfs.getSuper().getBlockSize();
		}
		buf = new byte[blockSize];
	}
	
//	private VFSBlock(VFS vfs) {
//		init(vfs);
//	}
	
	JAFSBlock(JAFS vfs, long bpos) throws JAFSException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JAFSException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		init(vfs);
		this.bpos = bpos;
		byteIdx = 0;
	}
	
	JAFSBlock(JAFS vfs, long bpos, int blockSize) throws JAFSException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JAFSException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		this.blockSize = blockSize;
		init(vfs);
		this.bpos = bpos;
		byteIdx = 0;
	}

	void initZeros() {
		Arrays.fill(buf, (byte)0);
	}
	
	void initOnes() {
		Arrays.fill(buf, (byte)0xff);
	}
	
	void setBlock(long bpos) throws JAFSException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JAFSException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		this.bpos = bpos;
		byteIdx = 0;
	}
	
	void seek(int b) throws JAFSException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JAFSException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		byteIdx = b;
	}
		
	void readFromDisk() throws IOException {
		long start = blockSize + bpos*blockSize;
		raf.seek(start);
		raf.read(buf);
		byteIdx = 0;
	}
	
	void flush() throws IOException, JAFSException {
		long start = blockSize + bpos*blockSize;
		long end = start + blockSize;
		if (end>raf.length()) {
			throw new JAFSException("Trying to write beyond filesize");
		}
		raf.seek(start);
		raf.write(buf);
	}

	int readByte() throws JAFSException {
		if (byteIdx>=blockSize) {
			throw new JAFSException("Trying to read beyond buffer");
		}
		return buf[byteIdx++] & 0xff;
	}

	void writeByte(int i) throws JAFSException {
		if (byteIdx>=blockSize) {
			throw new JAFSException("Trying to write beyond buffer");
		}
		buf[byteIdx++] = (byte)(i & 0xff);
	}

	int readBytes(byte a[], int start, int len) {
		int blockSpace = blockSize-byteIdx;
		int todo = a.length-start;
		if (todo>blockSpace) {
			todo=blockSpace;
		}
		if (todo>len) {
			todo=len;
		}
		for (int n=0; n<todo; n++) {
			a[start+n] = buf[byteIdx++];
		}
		return todo;
	}

	int writeBytes(byte a[], int start, int len) {
		int blockSpace = blockSize-byteIdx;
		int todo = a.length-start;
		if (todo>blockSpace) {
			todo=blockSpace;
		}
		if (todo>len) {
			todo=len;
		}
		for (int n=0; n<todo; n++) {
			buf[byteIdx++] = a[start+n];
		}
		return todo;
	}
		
	long readInt() throws JAFSException {
		int i = 0;
		i |= (long)(readByte() & 0xff)<<24;
		i |= (long)(readByte() & 0xff)<<16;
		i |= (long)(readByte() & 0xff)<< 8;
		i |= (long)(readByte() & 0xff);
		return i;
	}

	void writeInt(long l) throws JAFSException {
		writeByte((int)((l >> 24) & 0xff));
		writeByte((int)((l >> 16) & 0xff));
		writeByte((int)((l >>  8) & 0xff));
		writeByte((int) (l & 0xff));
	}

	long readLong() throws JAFSException {
		long l = 0;
		l |= ((long)(readByte() & 0xffL))<<56;
		l |= ((long)(readByte() & 0xffL))<<48;
		l |= ((long)(readByte() & 0xffL))<<40;
		l |= ((long)(readByte() & 0xffL))<<32;
		l |= ((long)(readByte() & 0xffL))<<24;
		l |= ((long)(readByte() & 0xffL))<<16;
		l |= ((long)(readByte() & 0xffL))<<8;
		l |= ((long)(readByte() & 0xffL));
		return l;
	}
	
	void writeLong(long l) throws JAFSException {
		writeByte((int)((l >> 56) & 0xffL));
		writeByte((int)((l >> 48) & 0xffL));
		writeByte((int)((l >> 40) & 0xffL));
		writeByte((int)((l >> 32) & 0xffL));
		writeByte((int)((l >> 24) & 0xffL));
		writeByte((int)((l >> 16) & 0xffL));
		writeByte((int)((l >>  8)& 0xffL));
		writeByte((int) (l & 0xffL));
	}

	byte[] readBytes(int i) throws JAFSException {
		byte[] b = new byte[i];
		for (int n=0; n<i; n++) {
			b[n] = (byte)(readByte() & 0xff);
		}
		return b;
	}
	
	void writeBytes(byte[] b) throws JAFSException {
		for (int n=0; n<b.length; n++) {
			writeByte(b[n] & 0xff);
		}
	}

	short readShort() throws JAFSException {
		short s = 0;
		s |= (short)(readByte() & 0xff)<<8;
		s |= (short)(readByte() & 0xff);
		return s;
	}

	void writeShort(short s) throws JAFSException {
		writeByte(((s >>  8) & 0xff));
		writeByte((s & 0xff));
	}

	long readLongVar() throws JAFSException {
		int shift = 0;
		long l = 0;
		int b = readByte();
		while ((b & 0x80)>0) {
			l |= (b & 0x7fL) << shift;
			shift += 7;
			b = readByte();
		}
		l |= b << shift;
		return l;
	}

	void writeLongVar(long l) throws JAFSException {
		while (l != (l & 0x7f)) {
			writeByte((int)(0x80 | (l & 0x7f)));
			l >>= 7;
		}
		writeByte((int)l);
	}
}
