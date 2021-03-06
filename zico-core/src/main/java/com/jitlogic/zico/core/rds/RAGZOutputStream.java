/**
 * Copyright 2012-2014 Rafal Lewczuk <rafal.lewczuk@jitlogic.com>
 * <p/>
 * This is free software. You can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p/>
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this software. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jitlogic.zico.core.rds;


import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static com.jitlogic.zico.core.ZicoUtil.fromUIntBE;

/**
 * Random access gzipped output stream.
 *
 * @author RLE <rafal.lewczuk@jitlogic.com>
 */
public class RAGZOutputStream extends OutputStream {

    public final static long DEFAULT_SEGSZ = 1048576;

    private static boolean useLock = true;

    public static void useLock(boolean _useLock) {
        useLock = _useLock;
    }

    public static RAGZOutputStream toFile(String path) throws IOException {
        return toFile(path, DEFAULT_SEGSZ);
    }

    public static RAGZOutputStream toFile(String path, long segsz) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path, "rw");
        return new RAGZOutputStream(f, segsz);
    }

    private long logicalLength;
    private long lastSavedPos = 0;

    private long maxSegSize;

    private RandomAccessFile outFile;
    private FileLock outLock = null;

    private long curSegSize = -1;  // Current segment size (uncompressed)
    private long curSegStart = 0;  // Current segment start position (physical - in file); gzip header starts at this position;

    private CRC32 crc;
    private Deflater deflater;

    private byte[] outputBuf = new byte[4096];


    public RAGZOutputStream(RandomAccessFile outFile, long maxSegSize) throws IOException {
        this.outFile = outFile;
        this.maxSegSize = maxSegSize;

        if (useLock) {
            outLock = this.outFile.getChannel().tryLock();
        }

        reopenSegment(outFile);
    }

    @Override
    public void write(int b) throws IOException {
        byte[] buf = {(byte) (b & 0xff)};
        write(buf, 0, 1);
    }


    @Override
    public synchronized void write(byte[] buf, int off, int len) throws IOException {

        if (buf == null) {
            throw new NullPointerException("Passed buffer is null.");
        } else if (off < 0 || len < 0 || off + len > buf.length) {
            throw new IndexOutOfBoundsException("Possible buffer overrun: off="
                    + off + ", len=" + len + ", buf.length=" + buf.length);
        } else if (len == 0) {
            return;
        }

        if (curSegSize < 0 || curSegSize >= maxSegSize) {
            nextSegment();
        }

        deflater.setInput(buf, off, len);
        deflate();

        curSegSize += len;
        logicalLength += len;
        crc.update(buf, off, len);
    }


    @Override
    public void close() throws IOException {
        finishSegment();
        if (outLock != null) {
            outLock.release();
        }
        outFile.close();
    }


    public long logicalLength() {
        return logicalLength;
    }


    public long physicalLength() throws IOException {
        return outFile.length();
    }


    public long lastSavedPos() {
        return lastSavedPos;
    }


    private void deflate() throws IOException {
        int len = outputBuf.length;
        while (len == outputBuf.length) {
            len = deflater.deflate(outputBuf, 0, outputBuf.length, Deflater.SYNC_FLUSH);
            outFile.write(outputBuf, 0, len);
        }
    }


    private static final int GZ_HLEN = 10 + 2;
    private static final int GZ_XLEN = 8;
    private static final int GZ_FLEN = 8;


    private static final byte[] GZ_HEADER = {
            0x1f, (byte) 0x8b, 0x08, 0x04,   // magic, compression and flags
            0x00, 0x00, 0x00, 0x00,   // mtime (will be undefined)
            0x00, 0x03, 0x08, 0x00    // XFL, OS, XLEN
    };


    private static final byte[] GZ_XTRA = {
            (byte) 'R', (byte) 'G', 0x04, 0x00,
            0x00, 0x00, 0x00, 0x00
    };


    private synchronized void reopenSegment(RandomAccessFile outFile) throws IOException {
        if (outFile.length() > 0) {
            List<RAGZSegment> segments = RAGZSegment.scan(outFile);
            if (segments.size() > 0) {
                RAGZSegment segment = segments.get(segments.size() - 1);
                if (segment.getPhysicalLen() < 3) { // Empty segment has exactly 2 bytes (resulting from compression)
                    outFile.setLength(segment.getPhysicalPos());
                    outFile.seek(segment.getPhysicalPos()-4);    // Segment length needs to be zeroed
                    outFile.write(fromUIntBE(0));
                    curSegStart = segment.getPhysicalPos() - 20;
                    curSegSize = 0;

                    deflater = new Deflater(6, true);
                    crc = new CRC32();
                    logicalLength = segment.getLogicalPos() + curSegSize;
                    lastSavedPos = logicalLength;
                } else {
                    curSegStart = segment.getPhysicalPos() - 20;
                    byte[] data = RAGZSegment.unpack(outFile, segment);
                    logicalLength = segment.getLogicalPos() + data.length;
                    curSegSize = data.length;
                    deflater = new Deflater(6, true);
                    crc = new CRC32();
                    crc.update(data);
                    nextSegment();
                }
            }
        } else {
            nextSegment();
        }
    }


    private void nextSegment() throws IOException {
        outFile.seek(outFile.length());

        if (curSegSize >= 0) {
            finishSegment();
        }

        curSegStart = outFile.getFilePointer();
        curSegSize = 0;

        outFile.write(GZ_HEADER);
        outFile.write(GZ_XTRA);

        deflater = new Deflater(6, true);
        crc = new CRC32();

        lastSavedPos = logicalLength;
    }


    private void finishSegment() throws IOException {
        if (deflater != null) {
            deflater.finish();
            deflate();
            outFile.write(fromUIntBE(crc.getValue()));
            outFile.write(fromUIntBE(curSegSize));
            long pos = outFile.getFilePointer();
            outFile.seek(curSegStart + 12 + 4);
            outFile.write(fromUIntBE(pos - curSegStart - 28));
            outFile.seek(pos);

            deflater = null;
            crc = null;
        }
    }


    public RandomAccessFile getOutFile() {
        return outFile;
    }
}
