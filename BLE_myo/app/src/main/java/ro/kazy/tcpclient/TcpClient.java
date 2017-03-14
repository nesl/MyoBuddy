package ro.kazy.tcpclient;

import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Description
 *
 * @author Catalin Prata
 *         Date: 2/12/13
 */
public class TcpClient {

    private String SERVER_IP = "192.168.0.8"; //your computer IP address
    private int SERVER_PORT = 1234;
    // message to send to the server
    private String mServerMessage;
    private String trainingMode = "Null";
    private String result = "training!";
    // sends message received notifications
    private OnMessageReceived mMessageListener = null;
    // while this is true, the server will continue running
    private boolean mRun = false;
    // used to send messages
    private PrintWriter mBufferOut;
    // used to read messages from the server
    private BufferedReader mBufferIn;
    private TextView resultTextView;

    /**
     * Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TcpClient(OnMessageReceived listener, String server_ip, int server_port) {
        SERVER_IP = server_ip;
        SERVER_PORT = server_port;
        mMessageListener = listener;
        resultTextView = resultTextView;
    }

    /*public TcpClient(OnMessageReceived listener)
    {
        mMessageListener = listener;
    }*/

    public String getResultStr()
    {
        return result;
    }

    public void setClientData(String server_ip, int server_port, String trainingMode)
    {
        SERVER_IP = server_ip;
        SERVER_PORT = server_port;
        trainingMode = trainingMode;
    }
    public void setTrainingMode(String trainingMode)
    {
        trainingMode = trainingMode;
    }

    /**
     * Sends the message entered by client to the server
     *
     * @param message text entered by client
     */
    public boolean sendMessage(String message) {
        if (mBufferOut != null && !mBufferOut.checkError()) {
            mBufferOut.println(message);
            mBufferOut.flush();

            Log.d("TCP Client", "sendMessage: " + message);
            //mMessageListener.messageReceived("ServerOK");
            return true;
        }
        else {
            Log.e("TCP Client", "cannot sendMessage to: " + SERVER_IP + ":" + SERVER_PORT);
            //mMessageListener.messageReceived("ServerError");
            return false;
        }
    }

    /**
     * Close the connection and release the members
     */
    public void stopClient() {

        //sendMessage("stop");
        mMessageListener.messageReceived("TCPServerStop");

        mRun = false;

        if (mBufferOut != null) {
            mBufferOut.flush();
            mBufferOut.close();
        }

        mMessageListener = null;
        mBufferIn = null;
        mBufferOut = null;
        mServerMessage = null;
    }

    public void run() {


        try {

            Log.e("TCP Client", "C: Connecting to " + SERVER_IP + ":" + SERVER_PORT);

            //here you must put your computer's IP address.
            InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
            //create a socket to make the connection with the server
            Socket socket = new Socket(serverAddr, SERVER_PORT);

            try {

                //sends the message to the server
                mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                //receives the message which the server sends back
                mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // send login name
                mRun = (sendMessage("start") == true);
                Log.d("TCP", "S: socket ok");

                mMessageListener.messageReceived("TCPServerStart");

                //in this while the client listens for the messages sent by the server
                while (mRun) {

                    if(mBufferIn != null) {

                        mServerMessage = mBufferIn.readLine();
                        if (mServerMessage != null)
                            Log.e("RESPONSE FROM SERVER", "S: Received Message: '" + mServerMessage + "'");

                        if(mServerMessage.equals("stop")) {
                            stopClient();
                            break;
                        }

                        if (mServerMessage != null && mMessageListener != null) {
                            //call the method messageReceived from MyActivity class
                            mMessageListener.messageReceived(mServerMessage);
                        }
                    }
                }

                socket.close();

                Log.d("TCP", "S: socket close");
            } catch (Exception e) {

                Log.e("TCP", "S: Error", e);

            } finally {
                //the socket must be closed. It is not possible to reconnect to this socket
                // after it is closed, which means a new socket instance has to be created.
                mRun = false;
                socket.close();
            }

        } catch (Exception e) {

            Log.e("TCP", "C: Error", e);

        }

    }

    //Declare the interface. The method messageReceived(String message) will must be implemented in the MyActivity
    //class at on asynckTask doInBackground
    public interface OnMessageReceived {
        public void messageReceived(String message);
    }
}
