package me.shedaniel.clothconfig2.internal;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class DiscordIpc {

    static final String CLIENT_ID = "1482797317194252288";
    private static final int IPC_VERSION = 1;
    private static final String[] PIPE_NAMES = {
            "\\\\.\\pipe\\discord-ipc-0",
            "\\\\.\\pipe\\discord-ipc-1",
            "\\\\.\\pipe\\discord-ipc-2",
            "\\\\.\\pipe\\discord-ipc-3",
            "\\\\.\\pipe\\discord-ipc-4",
            "\\\\.\\pipe\\discord-ipc-5",
            "\\\\.\\pipe\\discord-ipc-6",
            "\\\\.\\pipe\\discord-ipc-7",
            "\\\\.\\pipe\\discord-ipc-8",
            "\\\\.\\pipe\\discord-ipc-9"
    };

    private DiscordIpc() {}

    static RandomAccessFile openPipe() throws IOException {
        IOException last = null;
        for (String pipeName : PIPE_NAMES) {
            try {
                return new RandomAccessFile(pipeName, "rw");
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IOException("Discord IPC pipe not found");
    }

    static void handshake(RandomAccessFile pipe, String clientId) throws IOException {
        String payload = "{\"v\":" + IPC_VERSION + ",\"client_id\":\"" + clientId + "\"}";
        send(pipe, 0, payload);
    }

    static String readFrame(RandomAccessFile pipe) throws IOException {
        byte[] payload = read(pipe);
        return payload == null ? null : new String(payload, StandardCharsets.UTF_8);
    }

    private static void send(RandomAccessFile pipe, int opcode, String payload) throws IOException {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + data.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode);
        buffer.putInt(data.length);
        buffer.put(data);
        pipe.write(buffer.array());
    }

    private static byte[] read(RandomAccessFile pipe) throws IOException {
        byte[] header = new byte[8];
        int read = 0;
        while (read < 8) {
            int r = pipe.read(header, read, 8 - read);
            if (r < 0) {
                return null;
            }
            read += r;
        }
        ByteBuffer buffer = ByteBuffer.wrap(header);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.getInt();
        int length = buffer.getInt();
        if (length <= 0 || length > 1_048_576) {
            return null;
        }
        byte[] payload = new byte[length];
        read = 0;
        while (read < length) {
            int r = pipe.read(payload, read, length - read);
            if (r < 0) {
                return null;
            }
            read += r;
        }
        return payload;
    }
}
