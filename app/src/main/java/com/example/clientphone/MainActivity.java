package com.example.clientphone;

import java.io.IOException;
import java.io.InputStream;

import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.media.MediaPlayer;

//確定版
//ソケット通信
public class MainActivity extends Activity implements OnCheckedChangeListener,
		LocationListener {
	private final static String BR = System.getProperty("line.separator");
	private static final int LOCATION_UPDATE_MIN_TIME = 0;
	private static final int LOCATION_UPDATE_MIN_DISTANCE = 0;

	// IPアドレスの指定
	private final static String IP = "192.168.11.201";//"192.168.0.31";

	private TextView lblReceive;// 受信ラベル
	private TextView lblSend;
	private ToggleButton tb1, tb3;// トグルボタン

	private Socket socket; // ソケット
	private InputStream in; // 入力ストリーム
	private OutputStream out; // 出力ストリーム
	public boolean connected;


	private static final int SAMPLE_RATE = 8000; // Hertz
	//private static final int SAMPLE_INTERVAL = 20; // Milliseconds
	private static final int SAMPLE_frameRate = 50;
	//private static final int SAMPLE_SIZE = 2; // Bytes
	private static final int BUF_SIZE = SAMPLE_RATE / SAMPLE_frameRate;
	//private static final int BUF_SIZE = SAMPLE_INTERVAL * SAMPLE_INTERVAL * SAMPLE_SIZE; // Bytes
	//private static final int BUF_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10;


	protected static final String LOG_TAG = "MainActivity";
	private int TIME_BUF = 5;
	private final Handler handler = new Handler();// ハンドラ
	private boolean mic; // Enable mic?
	private boolean speakers = false; // Enable speakers?
	//boolean recordOrOutput = true;
	//boolean OutputOrNot = false;

	private AudioRecord audioRecorder = new AudioRecord(
			MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
			AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
			AudioRecord
					.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
							AudioFormat.ENCODING_PCM_16BIT) * 10);
	private LocationManager mLocationManager;
	private double longitude;
	private double latitude;
	private Timer timer;
	public Vibrator vibrater = null;
	public long pattern[] = {1000, 200, 700, 200, 400, 200};
	public static ToneGenerator toneGenerator;
	private AudioManager audioManager;
	//private int flag = 0;
	//private int count2 = 0;
	MediaPlayer mp = null;
	int cnt = 0;
	// 狩猟範囲：実験時は浜キャン内をエリアとする
	private double rangeLong_right = 137.719746;// 範囲
	private double rangeLong_left = 137.715123;// 範囲
	private double rangeLati_up = 34.727743;// 範囲
	private double rangeLati_bottom = 34.722923;// 範囲
	//private int millisec;
	private int others = 0;// 範囲内の端末数
	private int portnum = 50016;
	private int sendvoice = 0;
	//String deviceName = Build.DEVICE;

	// アクティビティ起動時に呼ばれる
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.activity_main);

		lblReceive = (TextView) findViewById(R.id.conText);
		lblSend = (TextView) findViewById(R.id.send);
		tb1 = (ToggleButton) findViewById(R.id.toggleButton1);
		tb3 = (ToggleButton) findViewById(R.id.toggleButton3);

		tb1.setTextOff("通話 Off");
		tb1.setTextOn("通話 On"); // トグルボタンのメッセージ
		tb1.setChecked(false); // OFFへ変更

		tb3.setTextOff("GPS Off");
		tb3.setTextOn("GPS On"); // トグルボタンのメッセージ
		tb3.setChecked(false); // OFFへ変更

		tb1.setOnCheckedChangeListener(this);
		tb3.setOnCheckedChangeListener(this);

		//radioPlayer(true);

		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		setVolumeControlStream(AudioManager.MODE_IN_COMMUNICATION);

		vibrater = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		toneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM,
				ToneGenerator.MAX_VOLUME);
		requestLocationUpdates();
	}

	//Start Micを確認
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		int check;

		//check toggle button 1
		check = 0;
		if (tb1.isChecked() == true) {

			// 音声送信処理.エリア内限定
			// if (rangeInorOut(longitude, latitude)) {
			startMic(true);
			// }
			check++;
		} else if (!tb1.isChecked() && check != 0) {
			// 音声送信切断処理.エリア内限定
			if (rangeInorOut(longitude, latitude)) {
				startMic(false);
			}
		}

		//check toggle button 3
		check = 0;
		if (tb3.isChecked() == true) {
			timerTask();
			check++;
		} else if (!tb3.isChecked() && check != 0) {
			timer.cancel();// 要修正．解あり
		}
	}

	// アクティビティの停止時に呼ばれる
	@Override
	public void onStop() {
		super.onStop();
		disconnect();
	}

	// 受信テキストの追加
	public void addText(final String text) {
		// ハンドラの生成

		handler.post(new Runnable() {
			public void run() {
				lblReceive.setText(text);
				//lblReceive.setText(text + BR + lblReceive.getText());
			}
		});
	}

	public void addText2(final String text) {
		// ハンドラの生成

		handler.post(new Runnable() {
			public void run() {
				lblSend.setText(text);
				//lblReceive.setText(text + BR + lblReceive.getText());
			}
		});
	}

	// アクティビティ開始時に呼ばれる
	@Override
	public void onStart() {
		super.onStart();
		// スレッドの生成
		Thread thread = new Thread() {
			public void run() {
				try {
					// 音声サーバに接続
					connect(IP, portnum);
					//radioPlayer(true);
				} catch (Exception e) {
				}
			}
		};
		thread.start();
	}

	// 接続
	private void connect(String ip, int port) {
		int bytesRead = 0;
		byte[] buf = new byte[BUF_SIZE];
		int recievevoice = 0;
		try {
			// ソケット接続
			addText("接続中");
			socket = new Socket(ip, port);
			in = socket.getInputStream();
			/*
			InputStreamReader inst = new InputStreamReader(in);
			BufferedReader br = new BufferedReader(inst);
			*/
			addText("接続完了");

			AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
					SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT, BUF_SIZE,
					AudioTrack.MODE_STREAM);
			track.play();
			// 受信ループ
			speakers = true;
			while (socket != null && socket.isConnected()) {
				// // データの受信
				while (speakers) {
					bytesRead = in.read(buf);
					// 送信時刻表示                                                                                                                                                    　　　　　　　　

					//if (recievevoice < 8&&sendvoice>0) {
					long currentTimeMillis = System.currentTimeMillis();
					Log.v("Test", String.valueOf(currentTimeMillis));

					Calendar calendar = Calendar.getInstance();
					calendar.setTimeInMillis(currentTimeMillis);
					//\addText("受信中----");
					addText2(calendar.get(Calendar.HOUR_OF_DAY) + ":"
							+ calendar.get(Calendar.MINUTE) + ":"
							+ calendar.get(Calendar.SECOND) + "."
							+ calendar.get(Calendar.MILLISECOND));
					//addText("受信完了----");
					//}


					recievevoice++;


					if (bytesRead <= 0)
						continue;
					Log.i(LOG_TAG, "Packet received: " + bytesRead);
					track.write(buf, 0, bytesRead);// 音声として書き込み
				}
				long currentTimeMillis = System.currentTimeMillis();
				Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(currentTimeMillis);

				track.stop();
				track.flush();
				track.release();
				speakers = false;
			}
			socket.close();
			return;
		} catch (Exception e) {
			addText("通信失敗しました");
		}
	}

	private void timerTask() {
		Timer t = new Timer();
		// 1分間に1回実行
		t.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				// String deviceName = Build.DEVICE;
				StringBuilder sb = new StringBuilder();
				sb.append(String.valueOf(longitude));
				sb.append(" ");
				sb.append(String.valueOf(latitude));
				sb.append(" ");
				// sb.append(deviceName);
				// sb.append(" ");
				String longlati = new String(sb);
				// 非同期処理
				new AsynConversation(MainActivity.this, longlati).execute();
			}
		}, 0, 60000);
	}

	// 切断
	private void disconnect() {
		try {
			socket.close();
			socket = null;
		} catch (Exception e) {
		}
	}

	public void startMic(boolean mic2) {
		addText("通信を開始します");
		// Creates the thread for capturing and transmitting audio
		mic = mic2;

		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				boolean isTalked = false;
				int i = 0;


				// Create an instance of the AudioRecord class
				Log.i(LOG_TAG, "Send thread started. Thread id: "
						+ Thread.currentThread().getId());
				int bytes_read = 0;
				int bytes_sent = 0;
				byte[] buf = new byte[BUF_SIZE];
				//byte[] buf = new byte[BUF_SIZE+TIME_BUF];
				try {
					out = socket.getOutputStream();
					// Create a socket and start recording
					audioRecorder.startRecording();

                    while (mic == true) {
						// Capture audio from the mic and transmit it

						// 12月03日に追加
						while(!isTalked){
							bytes_read = audioRecorder.read(buf, 0, BUF_SIZE);//送信1音声としてBUFSIZE分書き込むBUF_SIZE+TIME_BUF
							for(i=1;i<BUF_SIZE;i=i+2){
								if(Math.abs(buf[i])>20){
									isTalked = true;
									break;
								}
							}
							//Log.v("check", String.valueOf(isTalked));
						}

						//Log.v("isTalking", String.valueOf("yes"));

						bytes_read = audioRecorder.read(buf, 0, BUF_SIZE);//送信1音声としてBUFSIZE分書き込むBUF_SIZE+TIME_BUF
						try {
							/*
							long currentTimeMillis = System.currentTimeMillis();
							Calendar calendar = Calendar.getInstance();
							Log.v("Test", String.valueOf(currentTimeMillis));
							calendar.setTimeInMillis(currentTimeMillis);
							Log.v("Test",
									calendar.get(Calendar.HOUR_OF_DAY) + ":" +
											calendar.get(Calendar.MINUTE) + ":" +
											calendar.get(Calendar.SECOND) + ":" +
											calendar.get(Calendar.MILLISECOND));
							buf[BUF_SIZE] = (byte)calendar.get(Calendar.MINUTE);
							buf[BUF_SIZE + 1] = (byte)calendar.get(Calendar.SECOND);
							millisec = calendar.get(Calendar.MILLISECOND);
							//System.out.println(millisec);
							if (String.valueOf(millisec).length() == 3) {
								buf[BUF_SIZE+2] = (byte)(millisec / 100);
								System.out.println(buf[BUF_SIZE+2]);
								buf[BUF_SIZE+3] = (byte)((millisec / 10) % 10);
								System.out.println(buf[BUF_SIZE+3]);
								buf[BUF_SIZE+4] = (byte)(millisec % 10);
								System.out.println(buf[BUF_SIZE+4]);
							} else if (String.valueOf(millisec).length() == 2) {
								buf[BUF_SIZE+2] = 0;
								buf[BUF_SIZE+3] = (byte)(millisec / 10);
								buf[BUF_SIZE+4] = (byte)(millisec % 10);
							} else if (String.valueOf(millisec).length() == 1) {
								buf[BUF_SIZE+2] = 0;
								buf[BUF_SIZE+3] = 0;
								buf[BUF_SIZE+4] = (byte)millisec;
							}
							*/
							out.write(buf, 0, bytes_read);
							out.flush();
						} catch (IOException e) {
							e.printStackTrace();
							break;
						}
							bytes_sent += bytes_read;
							Log.i(LOG_TAG, "Total bytes sent: " + bytes_sent);
							////////////////////////////////////////////////////////////////////////////


							////////////////////////////////////////////////////////////////////////////
							// 送信時刻表示

							//if (sendvoice < 8) {
							long currentTimeMillis = System.currentTimeMillis();
							Log.v("Test", String.valueOf(currentTimeMillis));

							Calendar calendar = Calendar.getInstance();
							calendar.setTimeInMillis(currentTimeMillis);
							//addText("送信中----");
							addText(calendar.get(Calendar.HOUR_OF_DAY) + ":"
									+ calendar.get(Calendar.MINUTE) + ":"
									+ calendar.get(Calendar.SECOND) + "."
									+ calendar.get(Calendar.MILLISECOND));
							//addText("送信完了----");
							//}

							sendvoice++;
							// Thread.sleep(SAMPLE_INTERVAL, 0);
						}
					//}


					while (mic == false) {
						audioRecorder.stop();
						audioRecorder.release();
					}
					// audioRecorder.stop();
					// audioRecorder.release();
					disconnect();
					socket.close();
					mic = false;
					return;
				} catch (SocketException e) {
					Log.e(LOG_TAG, "SocketException: " + e.toString());
					mic = false;
				} catch (UnknownHostException e) {
					Log.e(LOG_TAG, "UnknownHostException: " + e.toString());
					mic = false;
				} catch (IOException e) {
					Log.e(LOG_TAG, "IOException: " + e.toString());
					mic = false;
				}
			}
		});
		thread.start();
	}

	@Override
	public void onLocationChanged(Location location) {
		showLocation(location);
	}

	// Called when the provider status changed.
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		showProvider(provider);
		switch (status) {
			case LocationProvider.OUT_OF_SERVICE:
				String outOfServiceMessage = provider + "圏外";
				showMessage(outOfServiceMessage);
				break;
			case LocationProvider.TEMPORARILY_UNAVAILABLE:
				String temporarilyUnavailableMessage = "一時的に" + provider
						+ "利用できません";
				showMessage(temporarilyUnavailableMessage);
				break;
			case LocationProvider.AVAILABLE:
				if (provider.equals(LocationManager.GPS_PROVIDER)) {
					String availableMessage = provider + "利用可能";
					showMessage(availableMessage);
					requestLocationUpdates();
				}
				break;
		}
	}

	// Called when the provider is enabled by the user.
	@Override
	public void onProviderEnabled(String provider) {
		String message = provider + "有効";
		showMessage(message);
		showProvider(provider);
		if (provider.equals(LocationManager.GPS_PROVIDER)) {
			requestLocationUpdates();
		}
	}

	// Called when the provider is disabled by the user.
	@Override
	public void onProviderDisabled(String provider) {
		showProvider(provider);
		if (provider.equals(LocationManager.GPS_PROVIDER)) {
			String message = provider + "無効";
			showMessage(message);
		}
	}

	private void requestLocationUpdates() {
		showProvider(LocationManager.GPS_PROVIDER);
		boolean isNetworkEnabled = mLocationManager
				.isProviderEnabled(LocationManager.GPS_PROVIDER);
		showNetworkEnabled(isNetworkEnabled);
		if (isNetworkEnabled) {
			mLocationManager.requestLocationUpdates(
					LocationManager.GPS_PROVIDER, LOCATION_UPDATE_MIN_TIME,
					LOCATION_UPDATE_MIN_DISTANCE, this);
			Location location = mLocationManager
					.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (location != null) {
				showLocation(location);
			}
		} else {
			String message = "Network無効";
			showMessage(message);
		}
	}

	// 範囲内，範囲外の表示
	private void showLocation(Location location) {
		longitude = location.getLongitude();
		latitude = location.getLatitude();
		if (longitude != 0 && latitude != 0) {
			if (rangeInorOut(longitude, latitude)) {
				// addText("範囲内にいます");
			} else {
				// addText("範囲外になりました");
			}
		}
	}

	private void radioPlayer(boolean flag){
		mp=MediaPlayer.create(this,R.raw.news2);
		/*
			if (mp.isPlaying()) { //再生中
				addText2("RADIO LISTEN");
				mp.stop();
				try {
					mp.prepare();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else { //停止
				mp.start();
			}

			//mp.stop();
			*/

		if(cnt>0){
			mp.setVolume(0,0);
		}else if(cnt==0) {
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
		}
		if(flag==true){
			mp.start();
		}else if(flag==false){
			mp.pause();
		}
	}



	private boolean rangeInorOut(double longitude, double latitude) {
		if (rangeLong_left < longitude && longitude < rangeLong_right
				&& rangeLati_bottom < latitude && latitude < rangeLati_up) {
			return true;
		}
		return false;
	}

	private void showMessage(String message) {
	}

	private void showProvider(String provider) {
	}

	private void showNetworkEnabled(boolean isNetworkEnabled) {
	}

	public int getOthers() {
		return others;
	}

	public void setOthers(int others) {
		this.others = others;
	}

}
