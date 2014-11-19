JAFS
====

####Java Application File System####
JAFS is a java library that will allow you to create a filesystem in a filesystem.

There is no stable release yet, but I have a working happy flow now.

####Why JAFS?####
* It is my way to learn how filesystems work
* It hides the files your application uses
* It is optimizable for lot's of small files by offering:
 * Smaller block size
 * Smaller inode size
 * Smaller maximum filesize
* It tries to keep the file system as small as possible (unused blocks bitmaps, inlined data)
* It will offer an API that is similar to java.io.File

####Design####
I am using the EXT2 file system as a source for ideas.

When creating a JAFS file system you will need to:
* supply a block size (2^n)
* inode size (<= block size)
* maximum file size. 

Based on this JAFS will create an inode layout. Then a single file will be created that holds the file system. The super block will be written to file and the file system is then initialized.

The directory structure follows more or less the ext2 design. It is therefor not an H-tree and pretty slow when it holds a lot of entries.

The inode will hold the following information:
* file type (file or directory)
* file size
* pointers to data or other pointer blocks

It does not contain timestamps, file permissions, etc.

The inode supports inlined data.

####How to use?####

I will create a kind of manual in the future, for the moment just look at the Main class that I created.
