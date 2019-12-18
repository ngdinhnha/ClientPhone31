package com.example.clientphone;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.os.AsyncTask;
import android.util.Log;

public class AsynConversation extends AsyncTask<Void, Void, Void> {
	public static final String IP ="192.168.11.201";// "192.168.0.31";
	public static final int PORT = 50002;//5556;2//50001,13,14,15
	public static final int BUFFER_SIZE = 8192;
	public static final int TIMEOUT = 5 * 1000;
	private Socket socket;
	private MainActivity mainActivity;
	private String longlati;
	private String otherIPs = null;

	public AsynConversation(MainActivity activity, String longlati) {
		this.mainActivity = activity;
		this.longlati = longlati;
	}

	@Override
	protected Void doInBackground(Void... v) {
		Log.i("debug", "Async start");
		if (connect() != false) {
			Log.i("debug", "connected");
			Log.i("debug", "Conversation start");
			handleConversation();
		} else {
			Log.i("debug", "cannot connect");
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void v) {
		if (otherIPs != null) {
			String[] ipAdd = otherIPs.split(" ", 0);
			if (ipAdd.length > 0) {

			}
			int count = ipAdd.length;
			int others = mainActivity.getOthers();

			if (count > others) {
				// 新たに端末が範囲内に入ってきたとき
				mainActivity.addText("新しい端末が範囲内に入りました");
				mainActivity.vibrater.vibrate(5000);

				mainActivity.addText("--------範囲内端末--------");
				for (int i = 0; i < ipAdd.length; i++) {
					mainActivity.addText(ipAdd[i]);
				}
				mainActivity.addText("-------------------------");
				mainActivity.setOthers(count);
			} else if (others > count) {
				// ある端末が範囲外になった時，バイブレーション通知
				mainActivity.addText("範囲外になった端末がいます");
				mainActivity.vibrater.vibrate(5000);

				mainActivity.addText("--------範囲内端末--------");
				for (int i = 0; i < ipAdd.length; i++) {
					mainActivity.addText(ipAdd[i]);
				}
				mainActivity.addText("-------------------------");
				mainActivity.setOthers(count);
			}
			longlati = null;
			otherIPs = null;
		}
		Log.i("debug", "Async end");
	}

	private boolean connect() {
		boolean connectFlag = false;
		try {
			this.socket = new Socket();
			socket.setReceiveBufferSize(BUFFER_SIZE);
			socket.connect(new InetSocketAddress(IP, PORT));
			socket.setSendBufferSize(BUFFER_SIZE);
			connectFlag = true;
		} catch (Exception e) {
			Log.i("debug", "connect(): " + e.toString());
		} finally {
			if (connectFlag != true)
				socket = null;
		}
		return connectFlag;
	}

	private void handleConversation() {
		DataOutputStream out = null;
		DataInputStream in = null;
		try {
			// 送信
			out = new DataOutputStream(socket.getOutputStream());
			out.writeUTF(longlati);
			out.flush();
			// 受信
			in = new DataInputStream(socket.getInputStream());
			otherIPs = in.readUTF();

			in.close();
			out.close();
		} catch (Exception e) {
			Log.i("debug", "handleConversation() RW: " + e.toString());
		} finally {
			try {
				in.close();
				out.close();
				socket.close();
			} catch (IOException e) {
				Log.i("debug", "handleConversation() close: " + e.toString());
			}
		}
	}
}
