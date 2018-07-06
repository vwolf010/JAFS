package nl.v4you.jafs;

import java.io.IOException;
import java.util.TreeSet;

/*
 * <ushort: next entry>
 * <uint: inode block> (must be 0 if not used)
 * <ushort: inode idx>
 * <byte: type> (f=file, d=directory)
 * <byte: filename length>
 * <string: filename>
 */
class JafsDir {
	Jafs vfs;
	JafsInode inode;
	
	static void createRootDir(Jafs vfs) throws JafsException, IOException {
		JafsInode inode = new JafsInode(vfs);
		inode.createInode(JafsInode.INODE_DIR);
		inode.flush();
		JafsDir dir = new JafsDir(vfs, inode);
		dir.initDir(inode.getBpos(), inode.getIdx());
		vfs.getSuper().setRootDirBpos(inode.getBpos());
		vfs.getSuper().setRootDirIdx(inode.getIdx());
		vfs.getSuper().flush();
	}
	
	JafsDir(Jafs vfs, JafsInode inode) throws JafsException, IOException {
		this.vfs = vfs;
		this.inode = inode;
		if (inode!=null) {
			if ((inode.type & JafsInode.INODE_DIR)==0) {
				throw new JafsException("supplied inode is not of type directory");
			}
		}
	}
	
	JafsDir(Jafs vfs, long bpos, int idx) throws JafsException, IOException {
		this.vfs = vfs;
		this.inode = new JafsInode(vfs, bpos, idx);
	}
		
	JafsDir(Jafs vfs, JafsDirEntry entry) throws JafsException, IOException {
		this.vfs = vfs;
		this.inode = new JafsInode(vfs, entry.bpos, entry.idx);
	}
		
	long getEntryPos(String name) throws JafsException, IOException {
		inode.reload();
		byte buf[] = name.getBytes("UTF-8");
		int strLen = buf.length;
		inode.seek(0, JafsInode.SEEK_SET);
		int nextEntry = inode.readShort();
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			inode.seek(7, JafsInode.SEEK_CUR); // skip bpos, idx and type
			int nameLength = inode.readByte();
			if (nameLength!=strLen) {
				inode.seek(startPos, JafsInode.SEEK_SET);
				inode.seek(nextEntry, JafsInode.SEEK_CUR);
			}
			else {
				int n=0;
				while ((n<nameLength) && (inode.readByte()==(buf[n] & 0xff))) {
					n++;
				}
				if (n==nameLength) {
					return startPos;
				}
				else {
					inode.seek(startPos, JafsInode.SEEK_SET);
					inode.seek(nextEntry, JafsInode.SEEK_CUR);
				}
			}
			nextEntry = inode.readShort();
		}
		return -1;
	}
	
	JafsDirEntry getEntry(String name) throws JafsException, IOException {
		if (inode==null) {
			return null;
		}
		else {
			long startPos = this.getEntryPos(name);
			if (startPos<0) {
				return null;
			}
			JafsDirEntry entry = new JafsDirEntry();
			inode.seek(startPos, JafsInode.SEEK_SET);
			entry.startPos = startPos;
			entry.parentBpos = inode.getBpos();
			entry.parentIdx = inode.getIdx();
			entry.bpos = inode.readInt();
			entry.idx = inode.readShort();
			entry.type = (byte)inode.readByte();
			entry.name = name;
			return entry;
		}
	}
		
	void updateEntry(JafsDirEntry entry) throws JafsException, IOException {
		byte nameBuf[] = entry.name.getBytes("UTF-8");
		inode.reload();
		inode.seek(entry.startPos, JafsInode.SEEK_SET);
		inode.writeInt(entry.bpos); // block position
		inode.writeShort(entry.idx); // inode index
		inode.writeByte(entry.type); // file type
		inode.writeByte(nameBuf.length);
		inode.writeBytes(nameBuf, 0, nameBuf.length); // file name
	}
	
	void deleteEntry(JafsDirEntry entry) throws JafsException, IOException {
		inode.reload();
		inode.seek(entry.startPos, JafsInode.SEEK_SET);
		inode.writeInt(0); // block position
		inode.writeShort(0); // inode index
		inode.writeByte(0); // file type
		inode.writeByte(0); // name length
	}
	
	JafsInode getInode(String name) throws JafsException, IOException {
		JafsDirEntry f = getEntry(name);
		if (f==null || f.bpos==0) {
			return null;
		}
		else {
			return new JafsInode(vfs, f.bpos, f.idx);
		}
	}

	int countActiveEntries() throws JafsException, IOException {
		int cnt = 0;
		inode.seek(0, JafsInode.SEEK_SET);
		int nextEntry = inode.readShort();
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			inode.seek(4+2+1, JafsInode.SEEK_CUR);
			cnt++;
			int len = inode.readByte();
			if (len==0) {
				cnt--;
			}
			else if ((len==1) && (inode.readByte()=='.')) {
				cnt--;
			}
			else if ((len==2) && (inode.readByte()=='.') && (inode.readByte()=='.')) {
				cnt--;
			}
			inode.seek(startPos+nextEntry, JafsInode.SEEK_SET);
			nextEntry = inode.readShort();
		}
		return cnt;
	}
	
	void createEntry(JafsDirEntry entry) throws JafsException, IOException {
		if (entry.name.contains("/")) {
			if (entry.isDirectory()) {
				throw new JafsException("Directory name ["+entry.name+"] should not contain a slash (/)");
			}
			else {
				throw new JafsException("File name ["+entry.name+"] should not contain a slash (/)");
			}
		}
		if (getEntry(entry.name)!=null) {
			throw new JafsException("Name ["+entry.name+"] already exists");
		}
		
		byte nameBuf[] = entry.name.getBytes("UTF-8");
		int nameLen = nameBuf.length;

		/*
		 * Find smallest space to store entry
		 */
		inode.seek(0, JafsInode.SEEK_SET);
		int nextEntry = inode.readShort();
		long newEntryStartPos = 0;
		int newEntrySpaceForName = 1000; // way above maximum dir entry name length of 255
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			int spaceForName = nextEntry-4-2-1;
			inode.seek(4+2+1, JafsInode.SEEK_CUR);
			int len = inode.readByte();
			if (len==0 && nameLen<spaceForName && spaceForName<newEntrySpaceForName) {
				newEntryStartPos = startPos;
				newEntrySpaceForName = spaceForName;
			}
			inode.seek(startPos, JafsInode.SEEK_SET);
			inode.seek(nextEntry, JafsInode.SEEK_CUR);
			nextEntry = inode.readShort();
		}
		
		/*
		 * Insert new node
		 */
		if (newEntryStartPos>0) {
			// Re-use an existing entry
			inode.seek(newEntryStartPos, JafsInode.SEEK_SET);
		}
		else {
			// Append to the end
			inode.seek(-2, JafsInode.SEEK_CUR);
			inode.writeShort(4 + 2 + 1 + 1 + nameBuf.length); // total size of entry			
		}
		inode.writeInt(entry.bpos); // block position
		inode.writeShort(entry.idx); // inode index
		inode.writeByte(entry.type); // file type
		inode.writeByte(nameBuf.length);
		inode.writeBytes(nameBuf, 0, nameBuf.length); // file name
		if (newEntryStartPos==0) {
			inode.writeShort(0);
		}
	}

	/**
	 * Creates the . and .. entry
	 * 
	 * @param parentBpos Block position of the parent directory inode
	 * @param parentIdx  Index within inode block of the parent directory inode
	 * @throws IOException 
	 * @throws IOException 
	 */
	void initDir(long parentBpos, int parentIdx) throws JafsException, IOException {
		inode.writeShort(0);
		JafsDirEntry entry = new JafsDirEntry();
		entry.bpos = inode.getBpos();
		entry.idx = inode.getIdx();
		entry.type = JafsDirEntry.TYPE_DIR;
		entry.name = ".";
		createEntry(entry);
		entry.bpos = parentBpos;
		entry.idx = parentIdx;
		entry.type = JafsDirEntry.TYPE_DIR;
		entry.name = "..";
		createEntry(entry);
	}
	
	boolean createNewEntry(String name, byte type, long bpos, int idx) throws JafsException, IOException {
		if (name.contains("/")) {
			if ((type & JafsDirEntry.TYPE_FILE)>0) {
				throw new JafsException("File name ["+name+"] should not contain a slash (/)");
			}
			else {
				throw new JafsException("Dir name ["+name+"] should not contain a slash (/)");
			}
		}
		if (getEntry(name)!=null) {
			// name already exists
			return false;
		}
		JafsDirEntry entry = new JafsDirEntry();
		entry.bpos = bpos;
		entry.idx = idx;
		entry.type = type;
		entry.name = name;
		createEntry(entry);		
		return true;
	}
	
	JafsDirEntry mkinode(String name, int type) throws JafsException, IOException {
		JafsDirEntry entry = getEntry(name);
		if (entry!=null) {
			JafsInode childInode = new JafsInode(vfs);
			childInode.createInode(type);
			if ((type & JafsInode.INODE_DIR)>0) {
				JafsDir dir = new JafsDir(vfs, childInode);
				dir.initDir(inode.getBpos(), inode.getIdx());
			}
			entry.bpos = childInode.getBpos();
			entry.idx = childInode.getIdx();
			updateEntry(entry);
			return entry;
		}
		return null;
	}

	String[] list() throws JafsException, IOException {
		TreeSet<String> l = new TreeSet<String>();
		inode.seek(0, JafsInode.SEEK_SET);
		int nextEntry = inode.readShort();
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			inode.seek(4+2+1, JafsInode.SEEK_CUR);
			int len = inode.readByte();
			if (len>0) {
				byte name[] = new byte[len];
				for (int n=0; n<len; n++) {
					name[n] = (byte)inode.readByte();
				}
				if (name.length==1 && name[0]=='.') {
					// ignore .
				}
				else if (name.length==2 && name[0]=='.' && name[1]=='.') {
					// ignore ..
				}
				else {
					l.add(new String(name, "UTF-8"));
				}
			}
			inode.seek(startPos+nextEntry, JafsInode.SEEK_SET);
			nextEntry = inode.readShort();
		}
		
		String result[] = new String[0];
		return l.toArray(result);
	}
}
