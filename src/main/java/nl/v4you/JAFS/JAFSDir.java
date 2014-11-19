package nl.v4you.JAFS;

import java.io.IOException;
import java.util.TreeSet;

import nl.v4you.JAFS.JAFS;
import nl.v4you.JAFS.JAFSException;

/*
 * <ushort: next entry>
 * <uint: inode block> (must be 0 if not used)
 * <ushort: inode idx>
 * <byte: type> (f=file, d=directory)
 * <byte: filename length>
 * <string: filename>
 */
class JAFSDir {
	JAFS vfs;
	JAFSInode inode;
	
	static void createRootDir(JAFS vfs) throws JAFSException, IOException {
		JAFSInode inode = new JAFSInode(vfs);
		inode.createInode(JAFSInode.INODE_DIR);
		inode.flush();
		JAFSDir dir = new JAFSDir(vfs, inode);
		dir.initDir(inode.getBpos(), inode.getIdx());
		vfs.getSuper().setRootDirBpos(inode.getBpos());
		vfs.getSuper().setRootDirIdx(inode.getIdx());
		vfs.getSuper().flush();
	}
	
	JAFSDir(JAFS vfs, JAFSInode inode) throws JAFSException, IOException {
		this.vfs = vfs;
		this.inode = inode;
		if (inode!=null) {
			if ((inode.type & JAFSInode.INODE_DIR)==0) {
				throw new JAFSException("supplied inode is not of type directory");				
			}
		}
	}
	
	JAFSDir(JAFS vfs, long bpos, int idx) throws JAFSException, IOException {
		this.vfs = vfs;
		this.inode = new JAFSInode(vfs, bpos, idx);
	}
		
	JAFSDir(JAFS vfs, JAFSDirEntry entry) throws JAFSException, IOException {
		this.vfs = vfs;
		this.inode = new JAFSInode(vfs, entry.bpos, entry.idx);
	}
		
	long getEntryPos(String name) throws JAFSException, IOException {
		inode.reload();
		int strLen = name.length();
		byte buf[] = name.getBytes("UTF-8");
		inode.seek(0, JAFSInode.SEEK_SET);
		int nextEntry = inode.readShort();
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			inode.seek(7, JAFSInode.SEEK_CUR); // skip bpos, idx and type
			int nameLength = inode.readByte();
			if (nameLength!=strLen) {
				inode.seek(startPos, JAFSInode.SEEK_SET);
				inode.seek(nextEntry, JAFSInode.SEEK_CUR);
			}
			else {
				int n=0;
				while ((n<nameLength) && (inode.readByte()==buf[n])) {
					n++;
				}
				if (n==nameLength) {
					return startPos;
				}
				else {
					inode.seek(startPos, JAFSInode.SEEK_SET);
					inode.seek(nextEntry, JAFSInode.SEEK_CUR);
				}
			}
			nextEntry = inode.readShort();
		}
		return -1;
	}
	
	JAFSDirEntry getEntry(String name) throws JAFSException, IOException {
		if (inode==null) {
			return null;
		}
		else {
			long startPos = this.getEntryPos(name);
			if (startPos<0) {
				return null;
			}
			JAFSDirEntry entry = new JAFSDirEntry();		
			inode.seek(startPos, JAFSInode.SEEK_SET);
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
		
	void updateEntry(JAFSDirEntry entry) throws JAFSException, IOException {
//		byte nameBuf[] = entry.name.getBytes("UTF-8");
		inode.reload();
		inode.seek(entry.startPos, JAFSInode.SEEK_SET);
		inode.writeInt(entry.bpos); // block position
		inode.writeShort(entry.idx); // inode index
		inode.writeByte(entry.type); // file type
		inode.writeByte(entry.name.length());
//		inode.writeBytes(nameBuf); // file name
	}
	
	void deleteEntry(JAFSDirEntry entry) throws JAFSException, IOException {
		inode.reload();
		inode.seek(entry.startPos, JAFSInode.SEEK_SET);
		inode.writeInt(0); // block position
		inode.writeShort(0); // inode index
		inode.writeByte(0); // file type
		inode.writeByte(0); // name length
	}
	
	JAFSInode getInode(String name) throws JAFSException, IOException {
		JAFSDirEntry f = getEntry(name);
		if (f==null || f.bpos==0) {
			return null;
		}
		else {
			return new JAFSInode(vfs, f.bpos, f.idx);
		}
	}

	int countActiveEntries() throws JAFSException, IOException {
		int cnt = 0;
		inode.seek(0, JAFSInode.SEEK_SET);
		int nextEntry = inode.readShort();
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			inode.seek(4+2+1, JAFSInode.SEEK_CUR);
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
			inode.seek(startPos+nextEntry, JAFSInode.SEEK_SET);
			nextEntry = inode.readShort();
		}
		return cnt;
	}
	
	void createEntry(JAFSDirEntry entry) throws JAFSException, IOException {
		if (entry.name.contains("/")) {
			if (entry.isDirectory()) {
				throw new JAFSException("Directory name ["+entry.name+"] should not contain a slash (/)");
			}
			else {
				throw new JAFSException("File name ["+entry.name+"] should not contain a slash (/)");
			}
		}
		if (getEntry(entry.name)!=null) {
			throw new JAFSException("Name ["+entry.name+"] already exists");
		}
		
		byte nameBuf[] = entry.name.getBytes("UTF-8");
		int nameLen = nameBuf.length;

		/*
		 * Find smallest space to store entry
		 */
		inode.seek(0, JAFSInode.SEEK_SET);
		int nextEntry = inode.readShort();
		long newEntryStartPos = 0;
		int newEntrySpaceForName = 1000; // way above maximum dir entry name length of 255
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			int spaceForName = nextEntry-4-2-1;
			inode.seek(4+2+1, JAFSInode.SEEK_CUR);
			int len = inode.readByte();
			if (len==0 && nameLen<spaceForName && spaceForName<newEntrySpaceForName) {
				newEntryStartPos = startPos;
				newEntrySpaceForName = spaceForName;
			}
			inode.seek(startPos, JAFSInode.SEEK_SET);
			inode.seek(nextEntry, JAFSInode.SEEK_CUR);
			nextEntry = inode.readShort();
		}
		
		/*
		 * Insert new node
		 */
		if (newEntryStartPos>0) {
			// Re-use an existing entry
			inode.seek(newEntryStartPos, JAFSInode.SEEK_SET);
		}
		else {
			// Append to the end
			inode.seek(-2, JAFSInode.SEEK_CUR);
			inode.writeShort(4 + 2 + 1 + 1 + nameBuf.length); // total size of entry			
		}
		inode.writeInt(entry.bpos); // block position
		inode.writeShort(entry.idx); // inode index
		inode.writeByte(entry.type); // file type
		inode.writeByte(entry.name.length());
		inode.writeBytes(nameBuf, 0, nameBuf.length); // file name
		if (newEntryStartPos==0) {
			inode.writeShort(0);
		}
	}

	/**
	 * Creates the . and .. entry
	 * 
	 * @param bpos Block position of the parent directory inode
	 * @param idx  Index within inode block of the parent directory inode
	 * @throws IOException 
	 * @throws IOException 
	 */
	void initDir(long parentBpos, int parentIdx) throws JAFSException, IOException {
		inode.writeShort(0);
		JAFSDirEntry entry = new JAFSDirEntry();
		entry.bpos = inode.getBpos();
		entry.idx = inode.getIdx();
		entry.type = JAFSDirEntry.TYPE_DIR;
		entry.name = ".";
		createEntry(entry);
		entry.bpos = parentBpos;
		entry.idx = parentIdx;
		entry.type = JAFSDirEntry.TYPE_DIR;
		entry.name = "..";
		createEntry(entry);
	}
	
	boolean createNewEntry(String name, byte type) throws JAFSException, IOException {
		if (name.contains("/")) {
			if ((type & JAFSDirEntry.TYPE_FILE)>0) {
				throw new JAFSException("File name ["+name+"] should not contain a slash (/)");
			}
			else {
				throw new JAFSException("Dir name ["+name+"] should not contain a slash (/)");
			}
		}
		if (getEntry(name)!=null) {
			// name already exists
			return false;
		}
		JAFSDirEntry entry = new JAFSDirEntry();
		entry.bpos = 0;
		entry.idx = 0;
		entry.type = type;
		entry.name = name;
		createEntry(entry);		
		return true;
	}
	
	JAFSDirEntry mkinode(String name, int type) throws JAFSException, IOException {
		JAFSDirEntry entry = getEntry(name);
		if (entry!=null) {
			JAFSInode childInode = new JAFSInode(vfs);
			childInode.createInode(type);
			if ((type & JAFSInode.INODE_DIR)>0) {
				JAFSDir dir = new JAFSDir(vfs, childInode);
				dir.initDir(inode.getBpos(), inode.getIdx());
			}
			entry.bpos = childInode.getBpos();
			entry.idx = childInode.getIdx();
			updateEntry(entry);
			return entry;
		}
		return null;
	}

	String[] list() throws JAFSException, IOException {
		TreeSet<String> l = new TreeSet<String>();
		inode.seek(0, JAFSInode.SEEK_SET);
		int nextEntry = inode.readShort();
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			inode.seek(4+2+1, JAFSInode.SEEK_CUR);
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
			inode.seek(startPos+nextEntry, JAFSInode.SEEK_SET);
			nextEntry = inode.readShort();
		}
		
		String result[] = new String[0];
		return l.toArray(result);
	}
}
