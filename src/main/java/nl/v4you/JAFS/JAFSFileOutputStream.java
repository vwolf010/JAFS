package nl.v4you.JAFS;

import java.io.IOException;
import java.io.OutputStream;

import nl.v4you.JAFS.JAFS;
import nl.v4you.JAFS.JAFSException;

public class JAFSFileOutputStream extends OutputStream {
	JAFS vfs;
	JAFSInode inode;
	String path;
	
	JAFSFileOutputStream(JAFS vfs, JAFSFile f, boolean append) throws JAFSException, IOException {
		this.vfs = vfs;
		if (!f.exists()) {
			if (!f.createNewFile()) {
				throw new JAFSException("Could not create "+f.getCanonicalPath());
			}
		}
		this.path = f.getCanonicalPath();
		JAFSDirEntry entry = f.getEntry(f.getCanonicalPath());
		if (entry!=null) {
			if (entry.bpos>0) {
				inode = new JAFSInode(vfs, entry);
				if (append) {
					inode.seek(0, JAFSInode.SEEK_END);
				}
			}
		}
	}
	
	@Override
	public void flush() throws IOException {
		super.flush();
	}
	
	@Override
	public void close() throws IOException {
		super.close();
	}

	@Override
	public void write(byte buf[], int start, int len) throws IOException {
		try {
			if (inode==null) {
				JAFSFile f = new JAFSFile(vfs, path);
				JAFSDir dir = new JAFSDir(vfs, f.getEntry(f.getParent()));
				dir.mkinode(f.getName(), JAFSInode.INODE_FILE);
				inode = new JAFSInode(vfs, f.getEntry(path));
			}
			inode.writeBytes(buf, start, len);
		} catch (JAFSException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}

	@Override
	public void write(byte buf[]) throws IOException {
		write(buf, 0, buf.length);
	}
	
	@Override
	public void write(int arg0) throws IOException {
		try {
			if (inode==null) {
				JAFSFile f = new JAFSFile(vfs, path);
				JAFSDir dir = new JAFSDir(vfs, f.getEntry(f.getParent()));
				dir.mkinode(f.getName(), JAFSInode.INODE_FILE);
				inode = new JAFSInode(vfs, f.getEntry(path));
			}
			inode.writeByte(arg0);
		} catch (JAFSException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}
}
