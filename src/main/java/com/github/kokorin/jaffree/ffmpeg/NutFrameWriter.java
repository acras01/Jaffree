/*
 *    Copyright 2017-2021 Denis Kokorin
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.github.kokorin.jaffree.ffmpeg;

import com.github.kokorin.jaffree.JaffreeException;
import com.github.kokorin.jaffree.Rational;
import com.github.kokorin.jaffree.nut.DataItem;
import com.github.kokorin.jaffree.nut.FrameCode;
import com.github.kokorin.jaffree.nut.Info;
import com.github.kokorin.jaffree.nut.NutFrame;
import com.github.kokorin.jaffree.nut.NutOutputStream;
import com.github.kokorin.jaffree.nut.NutWriter;
import com.github.kokorin.jaffree.nut.StreamHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * {@link NutFrameWriter} allows writing uncompressed (raw) Frames in Nut format.
 */
public class NutFrameWriter implements FrameInput.FrameWriter {
    private final FrameProducer producer;
    private final ImageFormat imageFormat;
    private final long frameOrderingBufferMillis;
    private final boolean generateImages;

    //PCM Signed Differential?
    private static final byte[] FOURCC_PCM_S32BE = {32, 'D', 'S', 'P'};


    private static final Logger LOGGER = LoggerFactory.getLogger(NutFrameWriter.class);

    /**
     * Creates {@link NutFrameWriter}.
     *
     * @param producer                  frame producer
     * @param imageFormat               video frames image format
     * @param frameOrderingBufferMillis frame reordering buffer length
     */
    public NutFrameWriter(final FrameProducer producer, final ImageFormat imageFormat,
                          final long frameOrderingBufferMillis) {
        this.producer = producer;
        this.imageFormat = imageFormat;
        this.frameOrderingBufferMillis = frameOrderingBufferMillis;
        this.generateImages = true;
    }

    /**
     * Creates {@link NutFrameWriter}.
     *
     * @param producer                  frame producer
     * @param imageFormat               video frames image format
     * @param frameOrderingBufferMillis frame reordering buffer length
     * @param generateImages generate BufferedImage images
     */
    public NutFrameWriter(final FrameProducer producer, final ImageFormat imageFormat,
                          final long frameOrderingBufferMillis, final boolean generateImages) {
        this.producer = producer;
        this.imageFormat = imageFormat;
        this.frameOrderingBufferMillis = frameOrderingBufferMillis;
        this.generateImages = generateImages;
    }


    /**
     * Writes media in Nut format to output stream and closes it.
     *
     * @param outputStream OutputStream output stream to write to
     */
    public void write(final OutputStream outputStream) throws IOException {
        NutWriter writer = new NutWriter(
                new NutOutputStream(outputStream),
                frameOrderingBufferMillis
        );
        write(writer);
        writer.writeFooter();
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void write(final NutWriter writer) throws IOException {
        List<Stream> streams = producer.produceStreams();
        LOGGER.debug("Streams: {}", streams.toArray());

        StreamHeader[] streamHeaders = new StreamHeader[streams.size()];
        Rational[] timebases = new Rational[streams.size()];

        for (int i = 0; i < streamHeaders.length; i++) {
            Stream stream = streams.get(i);
            validateStream(stream, i);

            StreamHeader streamHeader = createStreamHeader(stream, i);
            streamHeaders[i] = streamHeader;
            timebases[i] = new Rational(1, stream.getTimebase());
        }

        writer.setMainHeader(streams.size(), Short.MAX_VALUE, timebases, createFrameCodes());
        writer.setStreamHeaders(streamHeaders);
        writer.setInfos(new Info[0]);

        Frame frame;
        while ((frame = producer.produce()) != null) {
            LOGGER.trace("Frame: {}", frame);

            byte[] data = convertFrameData(frame, streamHeaders[frame.getStreamId()]);
            NutFrame nutFrame = new NutFrame(
                    frame.getStreamId(),
                    frame.getPts(),
                    data,
                    new DataItem[0],
                    new DataItem[0],
                    true,
                    false
            );

            LOGGER.trace("NutFrame: {}", nutFrame);
            writer.writeFrame(nutFrame);
        }
    }

    private void validateStream(Stream stream, int expectedId) {
        if (stream.getId() != expectedId) {
            throw new JaffreeException("Stream ids must start with 0 and increase by 1 subsequently!");
        }
        Objects.requireNonNull(stream.getType(), "Stream type must be specified");
        Objects.requireNonNull(stream.getTimebase(), "Stream timebase must be specified");
    }

    private StreamHeader createStreamHeader(Stream stream, int streamId) {
        switch (stream.getType()) {
            case VIDEO:
                return createVideoStreamHeader(stream, streamId);
            case AUDIO:
                return createAudioStreamHeader(stream, streamId);
            default:
                throw new JaffreeException("Unknown Track Type: " + stream.getType());
        }
    }

    private StreamHeader createVideoStreamHeader(Stream stream, int streamId) {
        Objects.requireNonNull(stream.getWidth(), "Width must be specified");
        Objects.requireNonNull(stream.getHeight(), "Height must be specified");

        return new StreamHeader(
                streamId,
                StreamHeader.Type.VIDEO,
                imageFormat.getFourCC(),
                streamId,
                0,
                60_000,
                0,
                EnumSet.noneOf(StreamHeader.Flag.class),
                new byte[0],
                new StreamHeader.Video(stream.getWidth(), stream.getHeight(), 1, 1,
                        StreamHeader.ColourspaceType.UNKNOWN),
                null
        );
    }

    private StreamHeader createAudioStreamHeader(Stream stream, int streamId) {
        Objects.requireNonNull(stream.getSampleRate(), "Samplerate must be specified");
        Objects.requireNonNull(stream.getChannels(), "Number of channels must be specified");

        return new StreamHeader(
                streamId,
                StreamHeader.Type.AUDIO,
                FOURCC_PCM_S32BE,
                streamId,
                0,
                60_000,
                0,
                EnumSet.noneOf(StreamHeader.Flag.class),
                new byte[0],
                null,
                new StreamHeader.Audio(new Rational(stream.getSampleRate(), 1),
                        stream.getChannels())
        );
    }

    private FrameCode[] createFrameCodes() {
        FrameCode[] frameCodes = new FrameCode[256];
        frameCodes[0] = FrameCode.INVALID;
        frameCodes[1] = new FrameCode(
                EnumSet.of(FrameCode.Flag.CODED_FLAGS),
                0,
                1,
                0,
                0,
                0,
                0,
                0
        );

        for (int i = 2; i < frameCodes.length; i++) {
            frameCodes[i] = FrameCode.INVALID;
        }

        return frameCodes;
    }

    private byte[] convertFrameData(Frame frame, StreamHeader streamHeader) {
        switch (streamHeader.streamType) {
            case VIDEO:
                return generateImages ? imageFormat.toBytes(frame.getImage()) : frame.getBuffer().array();
            case AUDIO:
                byte[] data = new byte[frame.getSamples().length * 4];
                ByteBuffer.wrap(data).asIntBuffer().put(frame.getSamples());
                return data;
            default:
                throw new JaffreeException("Unexpected track: " + streamHeader.streamId +
                        ", type: " + streamHeader.streamType);
        }
    }
}
