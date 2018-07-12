package nl.v4you.jafs;

import java.io.IOException;
import java.util.TreeSet;

/*
 * <ushort: next entry>
 * <uint: inode block> (must be 0 if not used)
 * <ushort: inode ipos>
 * <byte: type> (f=file, d=directory)
 * <byte: filename length>
 * <string: filename>
 */
class JafsDir {
    static final byte BA_SLASH[] = {'/'};
    static final byte BA_DOT[] = {'.'};
    static final byte BA_DOTDOT[] = {'.', '.'};

	Jafs vfs;
	JafsInode inode;

	byte tmp[] = new byte[512];
	
	static void createRootDir(Jafs vfs) throws JafsException, IOException {
		JafsInode inode = new JafsInode(vfs);
		inode.createInode(JafsInode.INODE_DIR);
		inode.flushInode();
		JafsDir dir = new JafsDir(vfs, inode);
		dir.initDir(inode.getBpos(), inode.getIpos());
		vfs.getSuper().setRootDirBpos(inode.getBpos());
		vfs.getSuper().setRootDirIpos(inode.getIpos());
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
	
	JafsDir(Jafs vfs, long bpos, int ipos) throws JafsException, IOException {
		this.vfs = vfs;
		this.inode = new JafsInode(vfs, bpos, ipos);
	}
		
	JafsDir(Jafs vfs, JafsDirEntry entry) throws JafsException, IOException {
		this.vfs = vfs;
		this.inode = new JafsInode(vfs, entry.bpos, entry.ipos);
	}
		
	long getEntryPos(byte name[]) throws JafsException, IOException {
		int strLen = name.length;
		inode.seek(0, JafsInode.SEEK_SET);
		int nextEntry = inode.readShort();
		while (nextEntry>0) {
			long startPos = inode.getFpos();
			inode.seek(7, JafsInode.SEEK_CUR); // skip bpos, ipos and type
			int nameLength = inode.readByte();
			if (nameLength!=strLen) {
				inode.seek(startPos, JafsInode.SEEK_SET);
				inode.seek(nextEntry, JafsInode.SEEK_CUR);
			}
			else {
                if (inode.readBytes(tmp, 0, nameLength)!=nameLength) {
                    throw new IllegalStateException("could not read file/dir name");
                };
				int n=0;
				while ((n<nameLength) && (tmp[n]==name[n])) {
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
	
	JafsDirEntry getEntry(byte name[]) throws JafsException, IOException {
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
			entry.parentIpos = inode.getIpos();
			entry.bpos = inode.readInt();
			entry.ipos = inode.readShort();
			entry.type = (byte)inode.readByte();
			entry.name = name;
			return entry;
		}
	}
		
	void updateEntry(JafsDirEntry entry) throws JafsException, IOException {
		byte nameBuf[] = entry.name;
		inode.seek(entry.startPos, JafsInode.SEEK_SET);
		inode.writeInt((int)entry.bpos); // block position
		inode.writeShort(entry.ipos); // inode index
		inode.writeByte(entry.type); // file type
		inode.writeByte(nameBuf.length);
		inode.writeBytes(nameBuf, 0, nameBuf.length); // file name
	}
	
	void deleteEntry(JafsDirEntry entry) throws JafsException, IOException {
		inode.seek(entry.startPos, JafsInode.SEEK_SET);
		inode.writeInt(0); // block position
		inode.writeShort(0); // inode index
		inode.writeByte(0); // file type
		inode.writeByte(0); // name length
	}
	
	JafsInode getInode(byte name[]) throws JafsException, IOException {
		JafsDirEntry f = getEntry(name);
		if (f==null || f.bpos==0) {
			return null;
		}
		else {
			return new JafsInode(vfs, f.bpos, f.ipos);
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
		if (Util.contains(entry.name, BA_SLASH)) {
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
		
		byte nameBuf[] = entry.name;
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
		int tLen=0;
		if (newEntryStartPos>0) {
			// Re-use an existing entry
			inode.seek(newEntryStartPos, JafsInode.SEEK_SET);
		}
		else {
			// Append to the end
			inode.seek(-2, JafsInode.SEEK_CUR);
            Util.shortToArray(tmp, tLen, 4 + 2 + 1 + 1 + nameBuf.length);
            tLen+=2;
		}
        Util.intToArray(tmp, tLen, (int)entry.bpos);
		tLen+=4;
        Util.shortToArray(tmp, tLen, entry.ipos);
        tLen+=2;
        tmp[tLen++] = entry.type;
        tmp[tLen++] = (byte)nameBuf.length;
        System.arraycopy(nameBuf, 0, tmp, tLen, nameBuf.length);
        tLen += nameBuf.length;
		if (newEntryStartPos==0) {
			Util.shortToArray(tmp, tLen, 0);
			tLen+=2;
		}
		inode.writeBytes(tmp, 0, tLen);
	}

	/**
	 * Creates the . and .. entry
	 * 
	 * @param parentBpos Block position of the parent directory inode
	 * @param parentIpos  Index within inode block of the parent directory inode
	 * @throws IOException 
	 * @throws IOException 
	 */
	void initDir(long parentBpos, int parentIpos) throws JafsException, IOException {
		inode.writeShort(0);
		JafsDirEntry entry = new JafsDirEntry();
		entry.bpos = inode.getBpos();
		entry.ipos = inode.getIpos();
		entry.type = JafsDirEntry.TYPE_DIR;
		entry.name = BA_DOT;
		createEntry(entry);
		entry.bpos = parentBpos;
		entry.ipos = parentIpos;
		entry.type = JafsDirEntry.TYPE_DIR;
		entry.name = BA_DOTDOT;
		createEntry(entry);
	}
	
	boolean createNewEntry(byte name[], byte type, long bpos, int ipos) throws JafsException, IOException {
		if (Util.contains(name, BA_SLASH)) {
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
		entry.ipos = ipos;
		entry.type = type;
		entry.name = name;
		createEntry(entry);		
		return true;
	}
	
	JafsDirEntry mkinode(byte name[], int type) throws JafsException, IOException {
		JafsDirEntry entry = getEntry(name);
		if (entry!=null) {
			JafsInode childInode = new JafsInode(vfs);
			childInode.createInode(type);
			if ((type & JafsInode.INODE_DIR)>0) {
				JafsDir dir = new JafsDir(vfs, childInode);
				dir.initDir(inode.getBpos(), inode.getIpos());
			}
			entry.bpos = childInode.getBpos();
			entry.ipos = childInode.getIpos();
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
