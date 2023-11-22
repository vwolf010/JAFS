package nl.v4you.jafs.internal;

public class JafsDirEntry {
	long startPos;
	long parentBpos;
	long bpos;
	int type;
	byte[] name;
	public boolean isFile() {
		return (type & JafsInode.INODE_FILE) != 0;
	}
	public boolean isDirectory() {
		return (type & JafsInode.INODE_DIR) != 0;
	}
	public long getBpos() {
		return bpos;
	}
	public long getParentBpos() {
		return parentBpos;
	}
	public void setName(byte[] name) {
		this.name = name;
	}
	public int getType() {
		return type;
	}
	public void setParentBpos(long parentBpos) {
		this.parentBpos = parentBpos;
	}
	public void setBpos(long bpos) {
		this.bpos = bpos;
	}
	public void setType(int type) {
		this.type = type;
	}
}
