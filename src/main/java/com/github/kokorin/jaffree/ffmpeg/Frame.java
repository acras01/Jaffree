/*
 *    Copyright  2017 Denis Kokorin
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

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 * Represents video/audio data to be encoded or has been decoded.
 *
 * <b>Note</b>: image and samples must not be changed after creating Frame instance.
 * Otherwise it may affect (or event corrupt) produced media, because Jaffree internally
 * maintains frame reordering buffer while producing video.
 *
 * @see FrameInput
 * @see FrameOutput
 */
public class Frame {
    private final int streamId;
    private final long pts;
    private BufferedImage image = null;
    private ByteBuffer buffer = null;
    private final int[] samples;

    /**
     * Creates {@link Frame}.
     *
     * @param streamId streamId
     * @param pts      pts in {@link Stream} timebase
     * @param samples  audio samples in PCM S32BE format
     * @see Stream#getTimebase()
     */
    protected Frame(final int streamId, final long pts, final int[] samples) {
        if (samples == null) {
            throw new IllegalArgumentException("Samples parameter must be non null");
        }

        this.streamId = streamId;
        this.pts = pts;
        this.image = null;
        this.buffer = null;
        this.samples = samples;
    }

    /**
     * Creates {@link Frame}.
     *
     * @param streamId streamId
     * @param pts      pts in {@link Stream} timebase
     * @param image    video frame image
     * @see Stream#getTimebase()
     */
    protected Frame(final int streamId, final long pts, final BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException(
                    "Image parameter must be non null");
        }

        this.streamId = streamId;
        this.pts = pts;
        this.image = image;
        this.buffer = null;
        this.samples = null;
    }

    /**
     * Creates {@link Frame}.
     *
     * @param streamId streamId
     * @param pts      pts in {@link Stream} timebase
     * @param buffer    video frame byte buffer
     * @see Stream#getTimebase()
     */
    protected Frame(final int streamId, final long pts, final ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException(
                    "Buffer parameter must be non null");
        }

        this.streamId = streamId;
        this.pts = pts;
        this.buffer = buffer;
        this.image = null;
        this.samples = null;
    }

    /**
     * @return stream id (starting with 0)
     */
    public int getStreamId() {
        return streamId;
    }

    /**
     * PTS in corresponding {@link Stream} timebase.
     * E.g. Track's timebase is 44100 (audio track), timecode is 4410 - it means 0.1 second
     * (100 milliseconds)
     *
     * @return timecode
     * @see Stream#getTimebase()
     */
    public long getPts() {
        return pts;
    }

    /**
     * Returns video frame image (or null if current frame isn't video frame).
     *
     * @return video frame image
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Returns video frame buffer (or null if current frame isn't video frame).
     *
     * @return video frame buffer
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * Returns audio samples (or null if current frame isn't audio frame).
     *
     * @return audio samples in PCM S32BE format
     */
    public int[] getSamples() {
        return samples;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Frame{"
                + "streamId=" + streamId
                + ", pts=" + pts
                + ", image?=" + (image != null)
                + ", buffer?=" + (buffer != null)
                + ", samples?=" + (samples != null)
                + '}';
    }

    /**
     * Creates video {@link Frame}, samples are set to null.
     *
     * @param streamId stream id (starting with 0)
     * @param pts      pts in {@link Stream} timebase
     * @param image    video frame image
     * @return video {@link Frame}
     * @see Stream#getTimebase()
     */
    public static Frame createVideoFrame(final int streamId, final long pts,
                                         final BufferedImage image) {
        return new Frame(streamId, pts, image);
    }

    /**
     * Creates video {@link Frame}, samples are set to null.
     *
     * @param streamId stream id (starting with 0)
     * @param pts      pts in {@link Stream} timebase
     * @param buffer    video frame byte buffer
     * @return video {@link Frame}
     * @see Stream#getTimebase()
     */
    public static Frame createVideoFrame(final int streamId, final long pts,
                                         final ByteBuffer buffer) {
        return new Frame(streamId, pts, buffer);
    }

    /**
     * Creates audio {@link Frame}, image is set to null.
     *
     * @param streamId streamId
     * @param pts      pts in {@link Stream} timebase
     * @param samples  audio samples in PCM S32BE format
     * @return audio {@link Frame}
     * @see Stream#getTimebase()
     */
    public static Frame createAudioFrame(final int streamId, final long pts, final int[] samples) {
        return new Frame(streamId, pts, samples);
    }

}
