package nl.v4you.JVFS;

import java.io.IOException;
import java.io.OutputStream;

import nl.v4you.JVFS.JVFS;
import nl.v4you.JVFS.JVFSException;

public class JVFSFileOutputStream extends OutputStream {
	JVFS vfs;
	JVFSInode inode;
	String path;
	
	JVFSFileOutputStream(JVFS vfs, JVFSFile f) throws JVFSException, IOException {
		this.vfs = vfs;
		this.path = f.getCanonicalPath();
		JVFSDirEntry entry = f.getEntry(f.getCanonicalPath());
		if (entry!=null) {
			if (entry.bpos>0) {
				inode = new JVFSInode(vfs, entry); 
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
				JVFSFile f = new JVFSFile(vfs, path);
				JVFSDir dir = new JVFSDir(vfs, f.getEntry(f.getParent()));
				dir.mkinode(f.getName(), JVFSInode.INODE_FILE);
				inode = new JVFSInode(vfs, f.getEntry(path));
			}
			inode.writeBytes(buf, start, len);
		} catch (JVFSException e) {
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
				JVFSFile f = new JVFSFile(vfs, path);
				JVFSDir dir = new JVFSDir(vfs, f.getEntry(f.getParent()));
				dir.mkinode(f.getName(), JVFSInode.INODE_FILE);
				inode = new JVFSInode(vfs, f.getEntry(path));
			}
			inode.writeByte(arg0);
		} catch (JVFSException e) {
			e.printStackTrace();
			throw new IOException("VFSExcepion wrapper: "+e.getMessage());
		}
	}
}
