package nl.v4you.JAFS.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import nl.v4you.JAFS.JAFS;
import nl.v4you.JAFS.JAFSException;
import nl.v4you.JAFS.JAFSFile;
import nl.v4you.JAFS.JAFSFileInputStream;
import nl.v4you.JAFS.JAFSFileOutputStream;

public class Main {

	static final String TEST_PATH = "c:/data/temp";
	static final String TEST_ARCHIVE = TEST_PATH + "/test.jafs";

	static void dumpTree(JAFSFile f) throws JAFSException, IOException {
		for (JAFSFile s : f.listFiles()) {
			System.out.println(s.getCanonicalPath());
			if (s.isDirectory()) {
				dumpTree(s);
			}
		}
	}
	
	public static void main(String[] args) {
		try {
			{
				byte buf[] = new byte[251]; // 251 is a prime
				for (int n=0; n<251; n++) {
					buf[n] = (byte)(n & 0xff);
				}				
				File q = new File(TEST_PATH + "/xx.txt");
				q.delete();
				FileOutputStream fos = new FileOutputStream(q);
				for (int n=0; n<((1024*1024)/251); n++) {
					fos.write(buf);
				}
				fos.close();
			}
			
			File x = new File(TEST_ARCHIVE);
			x.delete();
			
			JAFS vfs = new JAFS(TEST_ARCHIVE, 128,64,4L*1024L*1024L*1024L);
			{
				JAFSFile f = vfs.getFile("/content.txt");
				JAFSFileOutputStream fos = vfs.getFileOutputStream(f);
				fos.write("1234567890".getBytes());
				fos.close();
				if (f.length()==10) {
					System.out.println(" ok: file length is correct");
				}
				else {
					System.out.println("nok: file length is incorrect");
				}
				byte buf[] = new byte[(int)f.length()];
				JAFSFileInputStream fis = vfs.getFileInputStream(f);
				fis.read(buf);
				String str = new String(buf);
				if (str.equals("1234567890")) {
					System.out.println(" ok: file content matches");
				}
				else {
					System.out.println("nok: file content does not match");
				}
				fis.close();

				JAFSFile g = vfs.getFile("/renamed.txt");
				f.renameTo(g);
				if (g.length()==10) {
					System.out.println(" ok: file length is correct");
				}
				else {
					System.out.println("nok: file length is incorrect");
				}
				buf = new byte[(int)g.length()];
				fis = vfs.getFileInputStream(g);
				fis.read(buf);
				str = new String(buf);
				if (str.equals("1234567890")) {
					System.out.println(" ok: file content matches");
				}
				else {
					System.out.println("nok: file content does not match");
				}
				fis.close();
			}
			{
				JAFSFile f = vfs.getFile("/sub1");
				f.mkdir();
				if (f.exists()) {
					System.out.println("exists");
				}
				else {
					System.out.println("exists not");
				}
			}
			{
				JAFSFile f = vfs.getFile("/sub1/sub2");
				f.mkdir();
				if (f.exists()) {
					System.out.println("exists");
				}
				else {
					System.out.println("exists not");
				}
			}
			{
				JAFSFile f = vfs.getFile("/sub1/sub2/sub3");
				f.mkdir();
				if (f.exists()) {
					System.out.println("exists");
				}
				else {
					System.out.println("exists not");
				}
			}
			
			{
				FileInputStream fis = new FileInputStream(TEST_PATH+"/xx.txt");

				JAFSFile f = vfs.getFile("/sub1/sub2/sub3/xx.txt");
				f.createNewFile();				
				JAFSFileOutputStream fos = vfs.getFileOutputStream(f);
				
				byte buf[] = new byte[5*1000];
				int bread;
				while ((bread=fis.read(buf))>0) {
					fos.write(buf, 0, bread);
				}
				fis.close();
				fos.close();
				System.out.println("copy to vfs done");
			}
			
			{
				JAFSFile f = vfs.getFile("/sub1/sub2/sub3/xx.txt");
				JAFSFileInputStream fis = vfs.getFileInputStream(f);
				File q = new File(TEST_PATH+"/yy.txt");
				q.delete();
				FileOutputStream fos = new FileOutputStream(q);
				
				byte buf[] = new byte[5*1000];
				int bread;
				while ((bread=fis.read(buf))>0) {
					fos.write(buf, 0, bread);
				}
				fis.close();
				fos.close();

				System.out.println("copy from vfs done");
				
				f.delete();
				System.out.println("Delete done");
			}
			
			{
				JAFSFile f = vfs.getFile("/1/2/3/4");
				System.out.println("mkdirs test = "+f.mkdirs());
				f = vfs.getFile("/1/2/3/4");
				f.delete();
			}

			{
				JAFSFile f = vfs.getFile("/1/2/3/4");
				System.out.println("mkdirs test = "+f.mkdirs());
				f.delete();
			}
			
			{
				JAFSFile f = vfs.getFile("/1/2/3");
				JAFSFile g = vfs.getFile("/sub1/sub2/3");
				f.renameTo(g);
			}
			
			{
				JAFSFile f = vfs.getFile("/");
				dumpTree(f);
			}

//			if (f.exists()) {
//				System.out.println(f.getName()+" exists");
//			}
//			else {
//				System.out.println(f.getName()+" does not exist");
//			}
//			if (f.isFile()) {
//				System.out.println(f.getName()+" is a file");
//			}
//			if (f.isDirectory()) {
//				System.out.println(f.getName()+" is a directory");
//			}
//			f.mkdir();
			
//			File x = new File("t.txt");
//			System.out.println(x.getAbsolutePath());
//			System.out.println(x.getCanonicalPath());
			//VFSFile f = new VFSFile(vfs, "hallo.txt");
			//VFS vfs = new VFS("c:/temp/test.vfs");
	//		VFSInode inode = new VFSInode(vfs);
//			inode.createFile();
			//VFSDir dir = new VFSDir(vfs);
			//dir.setInode(0, 2);
			//dir.createEntry(inode.getBpos(), inode.getIdx(), VFSDir.TYPE_FILE, "hallo.txt");
			//dir.createEntry(inode.getBpos(), inode.getIdx(), VFSDir.TYPE_FILE, "hallo2.txt");
			//dir.createEntry(inode.getBpos(), inode.getIdx(), VFSDir.TYPE_FILE, "hallo3.txt");
			//VFSInode inode2 = new VFSInode(vfs);
//			inode2.openFile(inode.getBpos(), inode.getIdx());
//			inode2.seek(0);
//			inode2.writeByte('H');
//			inode2.writeByte('a');
//			inode2.writeByte('l');
//			inode2.writeByte('l');
//			inode2.writeByte('o');
//			inode2.writeByte('!');
			vfs.close();
			System.out.println("Done");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
