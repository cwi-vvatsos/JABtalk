package com.jabstone.jabtalk.basic.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import com.jabstone.jabtalk.basic.JTApp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class JTAudioRecorder {
	private static int[] sampleRates = new int[] { 44100, 22050, 11025, 8000 };
	private String TAG = JTAudioRecorder.class.getSimpleName();
	private AudioRecord recorder = null;
	private int bufferSize = 4096;
	private Thread recordingThread = null;
	private boolean isRecording = false;
	private String fileName = null;
	private int selectedRate = 0;
	private short selectedChannel = 1;
	private short selectedBPP = 16;
	private boolean trimSilence = false;

	public JTAudioRecorder(String fileTarget) {
		this(fileTarget, false);
	}

	public JTAudioRecorder(String fileTarget, boolean trimSilence) {
		fileName = fileTarget;
		this.trimSilence = trimSilence;
	}

	private String getFilename() {
		return fileName;
	}

	private String getTempFilename() {
		return fileName + ".raw";
	}
	
	private AudioRecord getAudioRecorder() {
	    for (int rate : sampleRates) {
	        for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT }) {
	            for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO }) {
	                try {
	                    bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

	                    if (bufferSize > 0) {
	                        // check if we can instantiate and have a success
	                        AudioRecord recorder = new AudioRecord(AudioSource.MIC, rate, channelConfig, audioFormat, bufferSize);

	                        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
	                        	selectedRate = rate;
	                        	selectedChannel = channelConfig == AudioFormat.CHANNEL_IN_STEREO ? (short)2 : (short)1;
	                        	selectedBPP = audioFormat == AudioFormat.ENCODING_PCM_16BIT ? (short)16 : (short)8;
	                        	
	                        	String format = audioFormat == AudioFormat.ENCODING_PCM_16BIT ? "PCM 16 Bit" : "PCM 8 Bit";
	                        	String channels = channelConfig == AudioFormat.CHANNEL_IN_STEREO ? "Stereo" : "Mono";
	                        	String diags = "Audio recorded using following settings: Rate: " + String.valueOf(rate) + "   " +
	                        			"Audio Format: " + format + "   " +
	                        			"Channel Config: " + channels;
	                        	JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO, diags);
	                        	return recorder;
	                        }	                            
	                    }
					} catch (Exception ignored) {
					}
	            }
	        }
	    }	
	    return null;
	}

	public void startRecording() throws Exception {
		recorder = getAudioRecorder();
		if(recorder == null) {
			throw new Exception("Could not initialize audio recorder");
		}

		int i = recorder.getState();
		if (i == 1)
			recorder.startRecording();

		isRecording = true;

		recordingThread = new Thread(new Runnable() {

			@Override
			public void run() {
				writeAudioDataToFile();
			}
		}, "AudioRecorder Thread");

		recordingThread.setPriority(Thread.MAX_PRIORITY);
		recordingThread.start();
	}

	private void writeAudioDataToFile() {
		byte data[] = new byte[bufferSize];
		String filename = getTempFilename();
		FileOutputStream os = null;

		try {
			os = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Could open file for audio recording");
		}

		int read;

		if (null != os) {
			while (isRecording) {
				read = recorder.read(data, 0, bufferSize);

				if (AudioRecord.ERROR_INVALID_OPERATION != read) {
					try {
						os.write(data);
					} catch (IOException e) {
						JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
					}
				}
			}

			try {
				os.close();
			} catch (IOException e) {
				JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
			}
		}
	}

	public void stopRecording() {
		if (null != recorder) {
			isRecording = false;
			int i = recorder.getState();
			if (i == 1)
				recorder.stop();
			recorder.release();

			recorder = null;
			recordingThread = null;
		}

		copyWaveFile(getTempFilename(), getFilename());
		deleteTempFile();
	}

	private void deleteTempFile() {
		File file = new File(getTempFilename());
		file.delete();
	}

	private void copyWaveFile(String inFilename, String outFilename) {
		FileOutputStream out = null;
		try {
			byte[] raw = readFully(inFilename);
			byte[] payload = trimSilence ? trimSilence(raw) : raw;

			out = new FileOutputStream(outFilename);
			WaveHeader wh = new WaveHeader(WaveHeader.FORMAT_PCM, selectedChannel,
					selectedRate, selectedBPP, payload.length);
			wh.write(out);
			out.write(payload);
		} catch (IOException e) {
			JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
		} finally {
			if (out != null) {
				try { out.close(); } catch (IOException ignored) {}
			}
		}
	}

	private byte[] readFully(String path) throws IOException {
		InputStream in = new FileInputStream(path);
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] chunk = new byte[bufferSize];
			int n;
			while ((n = in.read(chunk)) != -1) {
				buf.write(chunk, 0, n);
			}
			return buf.toByteArray();
		} finally {
			in.close();
		}
	}

	// Strips leading/trailing silence from PCM. The silence threshold is derived
	// from the recording's peak amplitude (adaptive to background noise level)
	// so words recorded on a noisy phone mic still get trimmed correctly.
	private byte[] trimSilence(byte[] pcm) {
		if (pcm.length == 0) {
			return pcm;
		}
		int bytesPerSample = (selectedBPP / 8) * selectedChannel;
		if (bytesPerSample <= 0) {
			return pcm;
		}
		int totalSamples = pcm.length / bytesPerSample;
		if (totalSamples == 0) {
			return pcm;
		}

		// Analyze in short RMS windows (20 ms). Anything above 15% of the peak
		// window RMS counts as speech; everything quieter is silence.
		int windowSamples = Math.max(1, selectedRate / 50);
		int windowCount = totalSamples / windowSamples;
		if (windowCount < 3) {
			return pcm;
		}

		double[] rms = new double[windowCount];
		double peakRms = 0.0;
		for (int w = 0; w < windowCount; w++) {
			long sumSq = 0;
			int base = w * windowSamples * bytesPerSample;
			for (int s = 0; s < windowSamples; s++) {
				int amp = sampleAmplitude(pcm, base + s * bytesPerSample);
				sumSq += (long) amp * amp;
			}
			double r = Math.sqrt((double) sumSq / windowSamples);
			rms[w] = r;
			if (r > peakRms) {
				peakRms = r;
			}
		}

		double threshold = peakRms * 0.15;
		int startWindow = -1;
		int endWindow = -1;
		for (int w = 0; w < windowCount; w++) {
			if (rms[w] >= threshold) {
				startWindow = w;
				break;
			}
		}
		for (int w = windowCount - 1; w >= 0; w--) {
			if (rms[w] >= threshold) {
				endWindow = w;
				break;
			}
		}

		if (startWindow < 0 || endWindow < 0 || endWindow < startWindow) {
			return pcm;
		}

		int padWindows = 5;   // ~100 ms of headroom on each side
		int firstWindow = Math.max(0, startWindow - padWindows);
		int lastWindow = Math.min(windowCount - 1, endWindow + padWindows);
		int startByte = firstWindow * windowSamples * bytesPerSample;
		int endByte = (lastWindow + 1) * windowSamples * bytesPerSample;
		if (endByte > pcm.length) {
			endByte = pcm.length;
		}
		int length = endByte - startByte;
		if (length <= 0) {
			return pcm;
		}

		JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO,
				"Trim: peakRms=" + (int) peakRms + " threshold=" + (int) threshold
						+ " windows " + firstWindow + "-" + lastWindow + " of " + windowCount
						+ " (" + pcm.length + " -> " + length + " bytes)");

		byte[] out = new byte[length];
		System.arraycopy(pcm, startByte, out, 0, length);
		return out;
	}

	private int sampleAmplitude(byte[] pcm, int byteIndex) {
		if (selectedBPP == 16) {
			int lo = pcm[byteIndex] & 0xFF;
			int hi = pcm[byteIndex + 1];   // signed on purpose
			int v = (hi << 8) | lo;
			return Math.abs(v);
		}
		// 8-bit PCM from AudioRecord is unsigned, centered at 128.
		return Math.abs((pcm[byteIndex] & 0xFF) - 128);
	}
}