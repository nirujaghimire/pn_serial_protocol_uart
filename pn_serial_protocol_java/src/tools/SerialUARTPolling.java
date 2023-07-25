package tools;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Stack;

public class SerialUARTPolling {

    private static final int START = 0;
    private static final int ID = 1;
    private static final int LEN = 2;
    private static final int DATA = 3;
    private static final int END = 4;

    private static final int IDLE = 0;
    private static final int SENDING = 1;
    private static final int RECEIVING = 2;

    private final CRC32 crc32;
    private final SerialPort port;

    private CanTransmitCallback canTransmitCallback =(status)-> {};
    private UartTransmitCallback uartTransmitCallback =()-> {};
    private CanReceiveCallback canReceiveCallback =(id, bytes)-> {};
    private UartReceiveCallback uartReceiveCallback =(bytes)-> {};
    private ConnectCallback connectCallback =()-> {};
    private DisconnectCallback disconnectCallback =()-> {};

    private final Queue<Byte> receiveQueue = new ArrayDeque<>();
    private final Stack<Integer> statusStack = new Stack<>();

    ///////////////////////////////HARDWARE CALLBACK////////////////////////////////

    private int bytesToBeReceived = 1;
    /**
     * It is called when there is data received in input buffer
     */
    private void dataReceivedCallback(byte[] receivedBytes){
        if(statusStack.peek() == IDLE){
            uartReceiveCallback.uartReceiveCallback(receivedBytes);
            return;
        }

        for(byte b:receivedBytes)
            receiveQueue.add(b);
        int size = receiveQueue.size();
        if(size>=bytesToBeReceived){
            byte[] bytes = new byte[size];
            Byte b;
            for(int i=0;i<size;i++) {
                b = receiveQueue.poll();
                if(b==null)
                    break;
                bytes[i] = b;
            }
            receivedBytes(bytes);
        }

    }

    /**
     * It is called when port is disconnected
     */
    private void disconnectedCallback(){
        disconnectCallback.disconnectCallback();
    }

    /**
     * It is called when data is written
     */
    private void dataWrittenCallback(){
        if(statusStack.peek()==IDLE){
            uartTransmitCallback.uartTransmitCallback();
        }
    }

    /////////////////////////////////PRIVATE///////////////////////////////

    private volatile boolean isByteReceived = false;
    private byte[] bytesReceived;
    /**
     * This is called whenever there is required data specified in @bytesToBeReceived
     * @param bytes     : bytes received
     */
    private void receivedBytes(byte[] bytes){
        if(statusStack.peek()==SENDING){
            bytesReceived = bytes;
            isByteReceived = true;
        }else if(statusStack.peek()==RECEIVING){
            canReceive(bytes);
        }
    }

    /**
     * This will send the bytes and wait for acknowledge
     * @param bytes         : bytes to be sent
     * @param ack           : ack to be received
     * @param time_out      : timeout in millisecond
     * @param num_of_try    : number of try
     * @return              : true for success
     *                      : false for failed
     */
    private boolean sendAndAck(byte[] bytes,byte[] ack,long time_out,int num_of_try){
        boolean isAckSuccess = false;
        for(int j=0;j<num_of_try;j++) {
            //Send bytes
            port.writeBytes(bytes, bytes.length);
            long tic = System.currentTimeMillis();


            isByteReceived = false;
            bytesToBeReceived = ack.length;
            while ((System.currentTimeMillis() - tic) <= time_out) {
                if(!isByteReceived)
                    continue;

                //Check if ack to be received is received
                isAckSuccess = Arrays.equals(ack,bytesReceived);
                if(isAckSuccess)
                    break;
            }
            if(isAckSuccess)
                break;
        }
        return isAckSuccess;
    }

    /**
     * This transmits CAN message
     * @param id        : ID of bytes
     * @param bytes     : Bytes to be sent
     */
    private void canTransmit(long id, byte[] bytes){
        int timeout = 1000;//Timeout in Millisecond
        int num_try = 10;//Num of try

        statusStack.push(SENDING);
        //Send 'S':'O'
        if(!sendAndAck("S".getBytes(),"O".getBytes(),timeout,num_try)) {
            canTransmitCallback.canTransmitCallback(false);
            return;
        }

        //Send id:id
        byte[] id_bytes = IntegerAndBytes.int32ToBytes(id);
        if(!sendAndAck(id_bytes,id_bytes,timeout,num_try)) {
            canTransmitCallback.canTransmitCallback(false);
            return;
        }

        //Send len:'O'
        byte[] len_bytes = IntegerAndBytes.int16ToBytes(bytes.length);
        if(!sendAndAck(len_bytes,"O".getBytes(),timeout,num_try)) {
            canTransmitCallback.canTransmitCallback(false);
            return;
        }

        //Send Data:crc
        int crc = crc32.calculate(bytes);
        byte[] crc_bytes = IntegerAndBytes.int32ToBytes(crc);
        boolean check = sendAndAck(bytes,crc_bytes,timeout,num_try);

        //Send '\0' or -1 :'O'
        if(!sendAndAck(check?("\0".getBytes()):(new byte[]{-1}),"O".getBytes(),timeout,num_try)) {
            canTransmitCallback.canTransmitCallback(false);
            return;
        }
        canTransmitCallback.canTransmitCallback(check);
        statusStack.pop();
    }

    /**
     * This transmits message directly
     * @param bytes     : Bytes to be transmitted
     */
    private void uartTransmit(byte[] bytes){
        port.writeBytes(bytes,bytes.length);
    }

    private int receiveStatus = START;
    private byte[] receiveData;
    private long can_ID;
    private long tic = 0;
    private final long RECEIVE_TIMEOUT = 1000;
    /**
     * This is called in each time received event is called
     * @param bytes     : bytes received
     */
    private void canReceive(byte[] bytes){
        if((System.currentTimeMillis()-tic)>RECEIVE_TIMEOUT && receiveStatus!=START){
            receiveStatus = 0;
            bytesToBeReceived = 1;
        }

        if(receiveStatus == START){
            if(Arrays.equals(bytes,"S".getBytes())){
                //Start byte
                bytesToBeReceived = 4;
                receiveStatus++;
                port.writeBytes("O".getBytes(),1);
                tic = System.currentTimeMillis();
            }
        }else if(receiveStatus == ID){
            //ID
            can_ID = IntegerAndBytes.bytesToInt32(bytes);
            bytesToBeReceived = 2;
            receiveStatus++;
            port.writeBytes(bytes,4);
            tic = System.currentTimeMillis();
        }else if(receiveStatus == LEN){
            //LEN
            bytesToBeReceived = IntegerAndBytes.bytesToInt16(bytes);
            receiveStatus++;
            port.writeBytes("O".getBytes(),1);
            tic = System.currentTimeMillis();
        }else if(receiveStatus == DATA){
            //DATA
            bytesToBeReceived = 1;
            receiveStatus++;
            receiveData = bytes;
            int crc = crc32.calculate(receiveData);
            port.writeBytes(IntegerAndBytes.int32ToBytes(crc),4);
            tic = System.currentTimeMillis();
        }else if(receiveStatus == END){
            //END
            bytesToBeReceived = 1;
            receiveStatus = START;
            if(Arrays.equals("\0".getBytes(),bytes))
                canReceiveCallback.canReceiveCallback(can_ID,receiveData);
            port.writeBytes("O".getBytes(),1);
            tic = System.currentTimeMillis();
        }
    }

    /////////////////////////////////PUBLIC////////////////////////////////
    public SerialUARTPolling(SerialPort port, int baud_rate){
        this.crc32 = new CRC32();
        this.port = port;

        this.statusStack.push(IDLE);
        this.port.setBaudRate(baud_rate);

        int event_type = SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
                |SerialPort.LISTENING_EVENT_DATA_WRITTEN
                |SerialPort.LISTENING_EVENT_DATA_RECEIVED;

        new SerialPortEvent(port,event_type);
        this.port.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return event_type;
            }

            @Override
            public void serialEvent(SerialPortEvent serialPortEvent) {
                if(serialPortEvent.getEventType()==SerialPort.LISTENING_EVENT_DATA_RECEIVED)
                    dataReceivedCallback(serialPortEvent.getReceivedData());
                else if(serialPortEvent.getEventType()==SerialPort.LISTENING_EVENT_PORT_DISCONNECTED)
                    disconnectedCallback();
                else if(serialPortEvent.getEventType()==SerialPort.LISTENING_EVENT_DATA_WRITTEN)
                    dataWrittenCallback();
            }
        });

    }

    /**
     * It connects the uart port
     * @return      : Status of connection
     */
    public boolean connect(){
        if(port.openPort()) {
            connectCallback.connectCallback();
            return true;
        }
        return false;
    }

    /**
     * It disconnects the port
     * @return      : Status of disconnection
     */
    public boolean disconnect(){
        if(port.closePort()) {
            disconnectCallback.disconnectCallback();
            return true;
        }
        return false;
    }

    /**
     * This method send the data
     * @param id        : Can ID of data bytes
     * @param bytes     : data bytes
     */
    public void send(int id,byte[] bytes){
        new Thread(()-> canTransmit(id, bytes)).start();
    }

    /**
     * This method send the data through UART
     * @param bytes     : data bytes to be sent
     */
    public void send(byte[] bytes){
        new Thread(()->uartTransmit(bytes)).start();
    }

    /**
     * It enables CAN transmit and receive mode
     */
    public void enableIdleMode(){
        statusStack.clear();
        statusStack.push(IDLE);
    }

    /**
     * It enables normal transmit and receive mode
     */
    public void enableCanMode(){
        statusStack.clear();
        statusStack.push(RECEIVING);
    }

    //////////////////////////////////SET CALLBACK//////////////////////////
    /**
     * Callback triggers after data is either sent successfully or failed
     * @param canTransmitCallback      : Callback function
     * @return                  : this
     */
    public SerialUARTPolling setCanTransmitCallback(CanTransmitCallback canTransmitCallback){
        this.canTransmitCallback = canTransmitCallback;
        return this;
    }

    /**
     * Callback triggers after data is received successfully
     * @param canReceiveCallback   : Callback function
     * @return                  : this
     */
    public SerialUARTPolling setCanReceiveCallback(CanReceiveCallback canReceiveCallback){
        this.canReceiveCallback = canReceiveCallback;
        return this;
    }

    /**
     * Callback triggers after data is either sent successfully or failed
     * @param uartTransmitCallback      : Callback function
     * @return                  : this
     */
    public SerialUARTPolling setUartTransmitCallback(UartTransmitCallback uartTransmitCallback){
        this.uartTransmitCallback = uartTransmitCallback;
        return this;
    }

    /**
     * Callback triggers after data is received successfully
     * @param uartReceiveCallback   : Callback function
     * @return                  : this
     */
    public SerialUARTPolling setUartReceiveCallback(UartReceiveCallback uartReceiveCallback){
        this.uartReceiveCallback = uartReceiveCallback;
        return this;
    }

    /**
     * Callback triggers after connected
     * @param connectCallback   : Callback function
     * @return                  : this
     */
    public SerialUARTPolling setConnectCallback(ConnectCallback connectCallback){
        this.connectCallback = connectCallback;
        return this;
    }

    /**
     * Callback triggers after disconnected
     * @param disconnectCallback: Callback function
     * @return                  : this
     */
    public SerialUARTPolling setDisconnectCallback(DisconnectCallback disconnectCallback){
        this.disconnectCallback = disconnectCallback;
        return this;
    }

    //////////////////////////////////INTERFACE//////////////////////////////
    @FunctionalInterface
    public static interface CanTransmitCallback {
        void canTransmitCallback(boolean status);
    }

    @FunctionalInterface
    public static interface CanReceiveCallback {
        void canReceiveCallback(long id, byte[] bytes);
    }

    @FunctionalInterface
    public static interface UartReceiveCallback{
        void uartReceiveCallback(byte[] bytes);
    }

    @FunctionalInterface
    public static interface UartTransmitCallback{
        void uartTransmitCallback();
    }

    @FunctionalInterface
    public static interface ConnectCallback{
        void connectCallback();
    }

    @FunctionalInterface
    public static interface DisconnectCallback{
        void disconnectCallback();
    }

}
