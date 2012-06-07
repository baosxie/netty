package io.netty.handler.logging;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;

public class StreamLoggingHandler
        extends LoggingHandler
        implements ChannelInboundHandler<Byte>, ChannelOutboundHandler<Byte> {

    private static final String NEWLINE = String.format("%n");

    private static final String[] BYTE2HEX = new String[256];
    private static final String[] HEXPADDING = new String[16];
    private static final String[] BYTEPADDING = new String[16];
    private static final char[] BYTE2CHAR = new char[256];

    static {
        int i;

        // Generate the lookup table for byte-to-hex-dump conversion
        for (i = 0; i < 10; i ++) {
            StringBuilder buf = new StringBuilder(3);
            buf.append(" 0");
            buf.append(i);
            BYTE2HEX[i] = buf.toString();
        }
        for (; i < 16; i ++) {
            StringBuilder buf = new StringBuilder(3);
            buf.append(" 0");
            buf.append((char) ('a' + i - 10));
            BYTE2HEX[i] = buf.toString();
        }
        for (; i < BYTE2HEX.length; i ++) {
            StringBuilder buf = new StringBuilder(3);
            buf.append(' ');
            buf.append(Integer.toHexString(i));
            BYTE2HEX[i] = buf.toString();
        }

        // Generate the lookup table for hex dump paddings
        for (i = 0; i < HEXPADDING.length; i ++) {
            int padding = HEXPADDING.length - i;
            StringBuilder buf = new StringBuilder(padding * 3);
            for (int j = 0; j < padding; j ++) {
                buf.append("   ");
            }
            HEXPADDING[i] = buf.toString();
        }

        // Generate the lookup table for byte dump paddings
        for (i = 0; i < BYTEPADDING.length; i ++) {
            int padding = BYTEPADDING.length - i;
            StringBuilder buf = new StringBuilder(padding);
            for (int j = 0; j < padding; j ++) {
                buf.append(' ');
            }
            BYTEPADDING[i] = buf.toString();
        }

        // Generate the lookup table for byte-to-char conversion
        for (i = 0; i < BYTE2CHAR.length; i ++) {
            if (i <= 0x1f || i >= 0x7f) {
                BYTE2CHAR[i] = '.';
            } else {
                BYTE2CHAR[i] = (char) i;
            }
        }
    }

    public StreamLoggingHandler() {
        super();
    }

    public StreamLoggingHandler(Class<?> clazz, LogLevel level) {
        super(clazz, level);
    }

    public StreamLoggingHandler(Class<?> clazz) {
        super(clazz);
    }

    public StreamLoggingHandler(LogLevel level) {
        super(level);
    }

    public StreamLoggingHandler(String name, LogLevel level) {
        super(name, level);
    }

    public StreamLoggingHandler(String name) {
        super(name);
    }
    @Override
    public ChannelBufferHolder<Byte> newOutboundBuffer(ChannelHandlerContext ctx)
            throws Exception {
        return ChannelBufferHolders.byteBuffer();
    }

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(ChannelHandlerContext ctx)
            throws Exception {
        return ChannelBufferHolders.byteBuffer();
    }


    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx)
            throws Exception {
        ChannelBuffer buf = ctx.inboundByteBuffer();
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, formatBuffer("RECEIVED", buf)));
        }
        ctx.nextInboundByteBuffer().writeBytes(buf);
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelFuture future)
            throws Exception {
        ChannelBuffer buf = ctx.outboundByteBuffer();
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, formatBuffer("WRITE", buf)));
        }
        ctx.nextOutboundByteBuffer().writeBytes(buf);
        ctx.flush(future);
    }

    protected String formatBuffer(String message, ChannelBuffer buf) {
        int length = buf.readableBytes();
        int rows = length / 16 + (length % 15 == 0? 0 : 1) + 4;
        StringBuilder dump = new StringBuilder(rows * 80 + message.length() + 16);

        dump.append(message).append('(').append(length).append('B').append(')');
        dump.append(
                NEWLINE + "         +-------------------------------------------------+" +
                NEWLINE + "         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |" +
                NEWLINE + "+--------+-------------------------------------------------+----------------+");

        final int startIndex = buf.readerIndex();
        final int endIndex = buf.writerIndex();

        int i;
        for (i = startIndex; i < endIndex; i ++) {
            int relIdx = i - startIndex;
            int relIdxMod16 = relIdx & 15;
            if (relIdxMod16 == 0) {
                dump.append(NEWLINE);
                dump.append(Long.toHexString(relIdx & 0xFFFFFFFFL | 0x100000000L));
                dump.setCharAt(dump.length() - 9, '|');
                dump.append('|');
            }
            dump.append(BYTE2HEX[buf.getUnsignedByte(i)]);
            if (relIdxMod16 == 15) {
                dump.append(" |");
                for (int j = i - 15; j <= i; j ++) {
                    dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
                }
                dump.append('|');
            }
        }

        if ((i - startIndex & 15) != 0) {
            int remainder = length & 15;
            dump.append(HEXPADDING[remainder]);
            dump.append(" |");
            for (int j = i - remainder; j < i; j ++) {
                dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
            }
            dump.append(BYTEPADDING[remainder]);
            dump.append('|');
        }

        dump.append(
                NEWLINE + "+--------+-------------------------------------------------+----------------+");

        return dump.toString();
    }
}
