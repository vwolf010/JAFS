package nl.v4you.jafs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

// <token><more_literal_length_bytes><literals><repeat_bytes><more_repeat_length_bytes>

public class LZ5 {

    private static final int MATCH_LEN_MIN = 3;
    private static final int WIN_MAX = 32750;

    public byte[] compress(byte strAsBytes[]) {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int i = strAsBytes.length;
        while (i>0x7f) {
            bos.write(0x80 | (i & 0x7f));
            i >>>= 7;
        }
        bos.write(i);

        int ptr=0;

        int litLen=0;
        int prefixLen=0;

        int lastIndexOfPrefix=-1;
        while (ptr<strAsBytes.length) {
            int lastIndexOf;
            if (lastIndexOfPrefix!=-1) {
                if (strAsBytes[ptr]==strAsBytes[lastIndexOfPrefix+prefixLen]) {
                    lastIndexOf = lastIndexOfPrefix;
                }
                else {
                    int winStart = ptr - WIN_MAX;
                    if (winStart<0) {
                        winStart=0;
                    }
                    lastIndexOf = byteArrayLastIndexOf(
                            strAsBytes,
                            winStart,
                            (lastIndexOfPrefix + prefixLen + 1) - winStart,
                            ptr - prefixLen,
                            prefixLen + 1);
                }
            }
            else {
                int winStart = ptr - WIN_MAX;
                if (winStart<0) {
                    winStart=0;
                }
                lastIndexOf = byteArrayLastIndexOf(
                        strAsBytes,
                        winStart,
                        ptr-winStart,
                        ptr - prefixLen,
                        prefixLen + 1);
            }
            if (lastIndexOf!=-1) {
                prefixLen++;
                lastIndexOfPrefix = lastIndexOf;
            }
            else {
                if (prefixLen==0) {
                    litLen++;
                    prefixLen=0;
                }
                else if (prefixLen<MATCH_LEN_MIN) {
                    litLen+=prefixLen;
                    prefixLen=0;
                    ptr--; // retry byte we could not find earlier
                }
                else {
                    int back = ptr-prefixLen-lastIndexOfPrefix;
                    writeSequence(strAsBytes, ptr-prefixLen-litLen, litLen, back, prefixLen-MATCH_LEN_MIN, bos);
                    litLen=0;
                    prefixLen=0;
                    ptr--; // retry byte we could not find earlier
                }
                lastIndexOfPrefix=-1;
            }
            ptr++;
        }
        if (prefixLen==0) {
            writeSequence(strAsBytes, ptr-prefixLen-litLen, litLen, 0, 0, bos);
        }
        else {
            int back = ptr-prefixLen-lastIndexOfPrefix;
            int prefixLenNormalized=prefixLen-MATCH_LEN_MIN;
            if (prefixLenNormalized<0) {
                prefixLenNormalized=0;
            }
            writeSequence(strAsBytes, ptr-prefixLen-litLen, litLen, back, prefixLenNormalized, bos);
        }
        return bos.toByteArray();
    }

    public byte[] decompress(byte in[]) {

        int pIn=0;

        int flen=0;
        int shift=0;
        while ((in[pIn] & 0x80) != 0) {
            flen |= (in[pIn++] & 0x7f) << shift;
            shift += 7;
        }
        flen |= (in[pIn++] & 0x7f) << shift;

        byte out[] = new byte[flen];

        int bytesLeft=flen; // decompressed size
        int pOut=0;
        while (bytesLeft>0) {
            int token = in[pIn++] & 0xff;
            int copyLen = token>>>4;
            if (copyLen==0xf) {
                int cpyMore = in[pIn++] & 0xff;
                copyLen += cpyMore;
                while (cpyMore==0xff) {
                    cpyMore = in[pIn++] & 0xff;
                    copyLen += cpyMore;
                }
            }
            if (copyLen!=0) {
                bytesLeft-=copyLen;
                System.arraycopy(in, pIn, out, pOut, copyLen);
                pIn+=copyLen;
                pOut+=copyLen;
            }

            int offset = in[pIn++] & 0xff;
            if ((offset & 0x80)!=0) {
                offset &= 0x7f;
                offset |= (in[pIn++] & 0xff) << 7;
            }
            int matchLen = token & 0xf;
            if (matchLen==0xf) {
                int cpyMore = in[pIn++] & 0xff;
                matchLen += cpyMore;
                while (cpyMore==0xff) {
                    cpyMore = in[pIn++] & 0xff;
                    matchLen += cpyMore;
                }
            }
            matchLen += MATCH_LEN_MIN;
            if (matchLen>bytesLeft) {
                matchLen=bytesLeft;
            }
            bytesLeft -= matchLen;
            while (matchLen>0) {
                int cStart = pOut - offset;
                int len=matchLen;
                if ((pOut-cStart)<matchLen) {
                    len = pOut-cStart;
                }
                System.arraycopy(out, cStart, out, pOut, len);
                pOut += len;
                matchLen -= len;
            }
        }
        return out;
    }

    private void writeSequence(byte strAsBytes[], int litStart, int litLen, int offset, int repLen, ByteArrayOutputStream bos) {
        int token = litLen>=0xf ? 0xf0 : (litLen<<4);
        token |= repLen>=0xf ? 0xf : repLen;
        bos.write(token);
        if ((litLen-0xf)>=0) {
            int bytesLeft=litLen-0xf;
            while (bytesLeft>=0xff) {
                bos.write(0xff);
                bytesLeft-=0xff;
            }
            bos.write(bytesLeft);
        }
        bos.write(strAsBytes, litStart, litLen);
        if (offset>0x7f) {
            bos.write(0x80 | (offset & 0x7f));
            bos.write((offset >>> 7) & 0xff);
        }
        else {
            bos.write(offset & 0x7f);
        }
        int bytesLeft=repLen-0xf;
        if (bytesLeft>=0) {
            while (bytesLeft>=0xff) {
                bos.write(0xff);
                bytesLeft-=0xff;
            }
            bos.write(bytesLeft);
        }
    }

    private int byteArrayLastIndexOf(byte str[], int strStart, int strLen, int prefixStart, int prefixLen) {

        if (strLen==0) {
            return -1;
        }
        strStart--;

        int matchLen=1;
        int lastByte = prefixStart+prefixLen-1;
        int n=strLen;

        startSearchForLastByte:
        while (true) {
            // find last byte
            while (n!=0 && str[strStart + n] != str[lastByte]) {
                n--;
            }
            if (n==0)
                break;
            n--;
            // will the rest match?
            while (n!=0 && matchLen!=prefixLen) {
                if (str[strStart + n] != str[lastByte - matchLen]) {
                    n+=matchLen-1;
                    matchLen=1;
                    continue startSearchForLastByte;
                }
                matchLen++;
                n--;
            }
            if (matchLen == prefixLen) {
                return strStart + n + 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) throws IOException {

        LZ5 lz4 = new LZ5();

        //File f = new File("c:/data/ggc/801042437.xml");
        File f = new File("c:/data/ggc/clustering.log");
        FileInputStream fis = new FileInputStream(f);
        byte b[] = new byte[(int)f.length()];
        fis.read(b);
        fis.close();

        //byte original[] = "hallo hallo hallo hallo hallo hallo".getBytes();
        //byte original[] = "hallo a hallo b hallo c hallo d hallo e hallo".getBytes();
        //byte original[] = " hallo aaaaa hallo hallo".getBytes();
        //byte original[] = " aaaaa bbbbb bbbbb bbbbbc".getBytes();
        //byte original[] = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
        byte original[] = b;

        //lz4.writeSequence("a".getBytes(), 1, 39);
        //lz4.writeSequence("abcbla".getBytes(), 3, 6);

        long t1 = System.currentTimeMillis();
        byte compressed[] = lz4.compress(original);
        System.err.println(System.currentTimeMillis()-t1);
//        FileOutputStream fos = new FileOutputStream(new File("c:/data/out.bin"));
//        fos.write(compressed);
//        fos.close();
        System.err.println(original.length+" : "+compressed.length);
        byte fDecompressed[] = lz4.decompress(compressed);
        //System.err.println(new String(fDecompressed, "UTF-8"));
        System.err.println(Arrays.equals(original, fDecompressed));

        //System.out.println(new String(Arrays.copyOf(back, len), Util.UTF8));
    }
}